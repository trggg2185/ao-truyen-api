package com.example.mymangaapp.mymangaapp.scheduler;

import com.example.mymangaapp.mymangaapp.service.ChapterViewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class ViewFlushScheduler {

    private static final int BATCH_SIZE = 500;

    /*
     * CÂU SQL 1: Cộng dồn lượt xem cho từng Chapter (batch update).
     * Dùng COALESCE(views, 0) để phòng trường hợp dữ liệu cũ trong DB có cột views bị NULL
     * (vì trong SQL: NULL + 5 = NULL!).
     */
    private static final String UPDATE_CHAPTER_VIEWS_SQL =
            "UPDATE chapter SET views = COALESCE(views, 0) + ? WHERE id = ?";

    /*
     * CÂU SQL 2: Cộng dồn tổng lượt xem (total_views) cho từng Manga tương ứng.
     */
    private static final String UPDATE_MANGA_VIEWS_SQL =
            "UPDATE manga SET total_views = COALESCE(total_views, 0) + ? WHERE id = ?";

    private final ChapterViewService chapterViewService;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;   // Spring Boot tự tạo sẵn bean này

    /**
     * Định kỳ mỗi 5 phút: lấy toàn bộ lượt xem đang chờ trong Redis và ghi (flush) xuống MySQL.
     *
     * TẠI SAO GỘP FLUSH CHAPTER VIEW VÀ MANGA TOTAL_VIEWS TRONG CÙNG 1 TRANSACTION?
     * 1. Không cần tốn thêm bộ nhớ Redis cho `manga:views`:
     *    Khi người dùng đọc chương (`GET /chapters/{chapterId}/pages`), API chỉ có `chapterId`.
     *    Nếu lưu thêm `manga:views` vào Redis lúc đọc trang, mỗi lượt đọc sẽ phải query DB để tìm `manga_id`
     *    từ `chapter_id` (làm mất ý nghĩa giảm tải DB của Redis!).
     *    Thay vào đó, ta chỉ cần tra cứu `chapter_id -> manga_id` 1 lần duy nhất cho cả lô (batch) 500 chương
     *    ngay trong lúc Scheduler chạy định kỳ 5 phút/lần.
     * 2. Đảm bảo tính nhất quán tuyệt đối (Atomicity):
     *    Cả `chapter.views` và `manga.total_views` đều được cộng trong CÙNG 1 Transaction của mỗi lô.
     *    - Nếu cả 2 thành công -> Commit DB -> Xóa các `chapterId` của lô đó khỏi Redis `PROCESSING_KEY`.
     *    - Nếu 1 trong 2 gặp lỗi (ví dụ deadlock, mất kết nối) -> Rollback cả 2 -> Giữ nguyên trong Redis
     *      để 5 phút sau chạy lại, KHÔNG BAO GIỜ bị lệch số liệu giữa tổng view của truyện và tổng view các chương!
     */
    @Scheduled(cron = "0 */5 * * * *")
    public void flushViews() {
        // Bước 1: Chốt danh sách (chapterId -> số view tăng thêm) từ Redis bằng lệnh nguyên tử RENAMENX
        Map<String, Long> claimed = chapterViewService.claimPendingViews();
        if (claimed.isEmpty()) {
            log.debug("Không có views để flush xuống db");
            return;
        }

        // Bước 2: Sắp xếp tăng dần theo chapterId.
        // Lý do: Khi nhiều transaction cùng khóa các dòng trong bảng `chapter`, việc luôn khóa theo
        // thứ tự bảng chữ cái của Primary Key (id) giúp triệt tiêu nguy cơ Deadlock trong InnoDB!
        List<Map.Entry<String, Long>> entries = claimed.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();

        int flushedChapters = 0;
        int failedChapters = 0;

        // Bước 3: Chia thành từng lô (batch) tối đa 500 chương để tránh giữ lock DB quá lâu
        for (int i = 0; i < entries.size(); i += BATCH_SIZE) {
            List<Map.Entry<String, Long>> chapterBatch =
                    entries.subList(i, Math.min(i + BATCH_SIZE, entries.size()));

            try {
                // Mỗi lô chạy trong 1 Transaction độc lập: cập nhật cả bảng `chapter` lẫn bảng `manga`
                transactionTemplate.executeWithoutResult(status -> flushBatch(chapterBatch));

                // Chỉ xóa khỏi Redis SAU KHI transaction DB đã commit thành công cả chapter lẫn manga
                List<String> processedChapterIds = chapterBatch.stream()
                        .map(Map.Entry::getKey)
                        .toList();
                chapterViewService.removeProcessed(processedChapterIds);

                flushedChapters += chapterBatch.size();
            } catch (Exception e) {
                // Lô bị lỗi đã được rollback tự động và vẫn nằm trong PROCESSING_KEY của Redis,
                // lần chạy 5 phút tiếp theo sẽ tự động thử lại mà không mất view nào.
                failedChapters += chapterBatch.size();
                log.error("Flush lỗi một lô {} chương, sẽ thử lại ở lần sau", chapterBatch.size(), e);
            }
        }

        log.info("Flush views hoàn tất: thành công {} chương, lỗi {} chương", flushedChapters, failedChapters);
    }

    /**
     * Thực thi trong 1 Transaction cho mỗi lô (batch):
     * 1. Batch UPDATE cộng view cho các dòng trong bảng `chapter`.
     * 2. Truy vấn `manga_id` của các `chapter` trong lô đó, gom tổng số view tăng thêm theo từng `manga_id`.
     * 3. Batch UPDATE cộng `total_views` cho các dòng tương ứng trong bảng `manga`.
     */
    private void flushBatch(List<Map.Entry<String, Long>> chapterBatch) {
        // 1. Cộng dồn views cho từng Chapter trong lô
        jdbcTemplate.batchUpdate(UPDATE_CHAPTER_VIEWS_SQL, chapterBatch, chapterBatch.size(), (ps, entry) -> {
            ps.setLong(1, entry.getValue());   // views = COALESCE(views, 0) + ?
            ps.setString(2, entry.getKey());   // WHERE id = ?
        });

        // 2. Gom tổng lượt xem tăng thêm cho từng Manga từ danh sách Chapter vừa cập nhật
        List<Map.Entry<String, Long>> mangaBatch = aggregateMangaViews(chapterBatch);
        if (mangaBatch.isEmpty()) {
            return;
        }

        // 3. Cộng dồn total_views cho từng Manga (đã được sort theo mangaId để chống Deadlock)
        jdbcTemplate.batchUpdate(UPDATE_MANGA_VIEWS_SQL, mangaBatch, mangaBatch.size(), (ps, entry) -> {
            ps.setLong(1, entry.getValue());   // total_views = COALESCE(total_views, 0) + ?
            ps.setString(2, entry.getKey());   // WHERE id = ?
        });
    }

    /**
     * Từ danh sách (chapterId -> viewDelta), truy vấn bảng `chapter` chỉ với 1 câu SQL `WHERE id IN (...)`
     * để lấy `manga_id`, sau đó cộng dồn tổng số view tăng thêm cho từng `manga_id`.
     *
     * Ví dụ:
     *   - Chapter A (thuộc Manga X) tăng 10 views
     *   - Chapter B (cũng thuộc Manga X) tăng 15 views
     *   => Gom lại: Manga X tăng tổng cộng 25 views (chỉ cần 1 lệnh UPDATE cho Manga X thay vì 2 lần!).
     *
     * Lưu ý: Nếu có chapterId nào vừa bị xóa khỏi DB trước khi kịp flush, câu SELECT sẽ không trả về
     * chapterId đó -> tự động bỏ qua an toàn mà không làm lỗi cả lô.
     */
    private List<Map.Entry<String, Long>> aggregateMangaViews(List<Map.Entry<String, Long>> chapterBatch) {
        // Map tra cứu nhanh: chapterId -> số view tăng thêm
        Map<String, Long> chapterDeltas = HashMap.newHashMap(chapterBatch.size());
        for (Map.Entry<String, Long> entry : chapterBatch) {
            if (entry.getValue() != null && entry.getValue() > 0) {
                chapterDeltas.put(entry.getKey(), entry.getValue());
            }
        }
        if (chapterDeltas.isEmpty()) {
            return Collections.emptyList();
        }

        // Tạo chuỗi (?, ?, ..., ?) tương ứng với số lượng chapterId trong lô
        String placeholders = String.join(",", Collections.nCopies(chapterDeltas.size(), "?"));
        String selectMangaIdsSql = "SELECT id, manga_id FROM chapter WHERE id IN (" + placeholders + ")";

        // Dùng TreeMap để tự động gom nhóm (sum) VÀ sắp xếp tăng dần theo mangaId (chống Deadlock khi UPDATE bảng manga)
        Map<String, Long> mangaDeltas = new TreeMap<>();

        jdbcTemplate.query(
                selectMangaIdsSql,
                rs -> {
                    String chapterId = rs.getString("id");
                    String mangaId = rs.getString("manga_id");
                    Long delta = chapterDeltas.get(chapterId);
                    if (mangaId != null && delta != null) {
                        mangaDeltas.merge(mangaId, delta, Long::sum);
                    }
                },
                chapterDeltas.keySet().toArray()
        );

        return new ArrayList<>(mangaDeltas.entrySet());
    }
}
