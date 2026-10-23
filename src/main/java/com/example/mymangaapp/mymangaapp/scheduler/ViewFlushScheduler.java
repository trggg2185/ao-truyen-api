package com.example.mymangaapp.mymangaapp.scheduler;

import com.example.mymangaapp.mymangaapp.service.ChapterViewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ViewFlushScheduler {

    private static final int BATCH_SIZE = 500;
    private static final String UPDATE_SQL = "UPDATE chapter SET views = views + ? WHERE id = ?";

    private final ChapterViewService chapterViewService;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;   // Spring Boot tự tạo sẵn bean này

    // Mỗi 5 phút
    @Scheduled(cron = "0 */5 * * * *")
    public void flushViews() {
        Map<String, Long> claimed = chapterViewService.claimPendingViews();
        if (claimed.isEmpty()) {
            log.debug("Không có views để flush xuống db");
            return;
        }

        // Sắp xếp theo id: các lô luôn khóa hàng theo cùng một thứ tự => tránh deadlock
        List<Map.Entry<String, Long>> entries = claimed.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .toList();

        int flushed = 0;
        int failed = 0;

        for (int i = 0; i < entries.size(); i += BATCH_SIZE) {
            List<Map.Entry<String, Long>> batch =
                    entries.subList(i, Math.min(i + BATCH_SIZE, entries.size()));

            try {
                // Mỗi lô là một transaction: hoặc áp dụng hết hoặc không gì cả
                transactionTemplate.executeWithoutResult(status -> updateViews(batch));

                // Chỉ xóa khỏi Redis SAU KHI DB đã commit
                chapterViewService.removeProcessed(batch.stream().map(Map.Entry::getKey).toList());
                flushed += batch.size();
            } catch (Exception e) {
                // Lô lỗi vẫn nằm trong "processing", lần chạy sau sẽ thử lại. Các lô khác vẫn tiếp tục.
                failed += batch.size();
                log.error("Flush lỗi một lô {} chương, sẽ thử lại ở lần sau", batch.size(), e);
            }
        }

        log.info("Flush views: thành công {} chương, lỗi {} chương", flushed, failed);
    }

    private void updateViews(List<Map.Entry<String, Long>> batch) {
        // batchSize = batch.size(): gửi trọn lô trong một lần
        jdbcTemplate.batchUpdate(UPDATE_SQL, batch, batch.size(), (ps, entry) -> {
            ps.setLong(1, entry.getValue());   // views + ?
            ps.setString(2, entry.getKey());   // WHERE id = ?
        });
    }
}
