package com.example.mymangaapp.mymangaapp.service;

import com.example.mymangaapp.mymangaapp.dto.chapter.request.ChapterRequest;
import com.example.mymangaapp.mymangaapp.dto.chapter.response.ChapterResponse;
import com.example.mymangaapp.mymangaapp.dto.chapter.response.ChapterSummaryResponse;
import com.example.mymangaapp.mymangaapp.dto.common.PaginatedResponse;
import com.example.mymangaapp.mymangaapp.entity.Chapter;
import com.example.mymangaapp.mymangaapp.entity.Page;
import com.example.mymangaapp.mymangaapp.enums.TransGroupStatus;
import com.example.mymangaapp.mymangaapp.exception.AppException;
import com.example.mymangaapp.mymangaapp.exception.ResponseCode;
import com.example.mymangaapp.mymangaapp.mapper.ChapterMapper;
import com.example.mymangaapp.mymangaapp.repository.ChapterRepository;
import com.example.mymangaapp.mymangaapp.repository.MangaRepository;
import com.example.mymangaapp.mymangaapp.security.utils.SecurityUtils;
import com.example.mymangaapp.mymangaapp.service.storage.ImageProcessor;
import com.example.mymangaapp.mymangaapp.service.storage.ObjectStorage;
import com.example.mymangaapp.mymangaapp.utils.StorageKeysUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class ChapterService {

    ChapterRepository chapterRepository;
    MangaRepository mangaRepository;

    ChapterMapper chapterMapper;

    PageService pageService;

    ObjectStorage objectStorage;


    // ------------------chức năng dành cho translator hoặc admin nhưng vẫn phải là thành viên của nhóm mới được------------------------//

    @PreAuthorize("hasAnyRole('ADMIN', 'TRANSLATOR')")
    @Transactional
    public ChapterResponse createChapter(@NonNull String mangaId, @NonNull ChapterRequest request) {

        // 1. Validate đầu vào trước: không chạm DB, không chạm R2
        List<String> tmpKeys = resolveTmpKeys(request.getPageUrls());

        // 2. Tồn tại + quyền
        if (!mangaRepository.existsById(mangaId)) {
            throw new AppException(ResponseCode.MANGA_NOT_FOUND);
        }
        checkCanCreateChapter(mangaId);

        // Kiểm tra nhanh để báo lỗi sớm (không bắt buộc, chỉ để đỡ tốn một lần insert)
        if (chapterRepository.existsByMangaIdAndChapterIndex(mangaId, request.getChapterIndex())) {
            throw new AppException(ResponseCode.CHAPTER_INDEX_ALREADY_EXISTED);
        }

        // 3. Insert chapter NGAY để DB chặn trùng index trước khi tốn công copy ảnh
        Chapter chapter = chapterMapper.toChapter(request);
        chapter.setManga(mangaRepository.getReferenceById(mangaId));
        chapter = insertChapter(chapter);

        // 4. Copy ảnh từ tmp/ sang vị trí chính thức
        List<String> pageUrls = copyPagesToFinalLocation(mangaId, chapter.getId(), tmpKeys);

        // 5. Tạo pages
        List<Page> pages = pageService.createPages(chapter, pageUrls);
        chapter.getPages().addAll(pages);

        log.info("Đã tạo chapter {} (index {}) với {} trang cho manga {}",
                chapter.getId(), chapter.getChapterIndex(), pages.size(), mangaId);

        return chapterMapper.toChapterResponse(chapter);
    }

    @PreAuthorize("hasAnyRole('ADMIN', 'TRANSLATOR')")
    @Transactional
    public void deleteChapterById(@NonNull String mangaId, @NonNull String chapterId) {

        Chapter chapter = chapterRepository
                .findById(chapterId)
                .orElseThrow(() -> new AppException(ResponseCode.CHAPTER_NOT_FOUND));

        // Check chapter có thuộc về manga này ko
        if (!chapter.getManga().getId().equals(mangaId)) {
            throw new AppException(ResponseCode.UNAUTHORIZED);
        }

        String currentUserId = SecurityUtils.getCurrentUserId();

        // user phải là thành viên hoặc leader của các nhóm đang dịch truyện này
        // và nhóm dịch phải hoạt động thì mới được tạo chương
        if (!mangaRepository.isMemberOrLeaderOfAnyGroupOfManga(currentUserId, mangaId, TransGroupStatus.APPROVED)) {
            throw new AppException(ResponseCode.UNAUTHORIZED);
        }

        // xoá chapter có cascade và orphanremoval nên page thuộc
        // về chapter cũng sẽ tự động xoá
        chapterRepository.delete(chapter);

        String prefix = StorageKeysUtils.chapterPrefix(mangaId, chapterId);

        // gọi xoá tất cả files trong prefix
        objectStorage.deleteFilesWithPrefix(prefix, Instant.now());

    }


    // ------------------------------chức năng public (cho khách)---------------------------------//

    // user thì ai cx vào manga bất kỳ và đều đọc đc các chapters
    // ==> để public method này
    public PaginatedResponse<ChapterSummaryResponse> getAllChaptersByMangaId(
            @NonNull String mangaId, int page,
            int size, @NonNull String sortBy
    ) {

        if (!mangaRepository.existsById(mangaId)) {
            throw new AppException(ResponseCode.MANGA_NOT_FOUND);
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, sortBy));

        org.springframework.data.domain.Page<ChapterSummaryResponse> dtoPage = chapterRepository
                .findSummariesByMangaId(mangaId, pageable);

        return PaginatedResponse.of(dtoPage);
    }


    // -----------------------------------------private method----------------------------------------------------//

    // Url -> key, và chỉ chấp nhận file nằm trong tmp/
    private List<String> resolveTmpKeys(List<String> tmpUrls) {
        return tmpUrls.stream()
                .map(url -> {
                    String key = objectStorage.toKey(url);   // ném URL_INVALID nếu không thuộc CDN của mình
                    if (!StorageKeysUtils.isTmp(key)) {
                        throw new AppException(ResponseCode.FILE_INVALID);
                    }
                    return key;
                })
                .toList();
    }


    private void checkCanCreateChapter(String mangaId) {
        String userId = SecurityUtils.getCurrentUserId();
        if (!mangaRepository.isMemberOrLeaderOfAnyGroupOfManga(userId, mangaId, TransGroupStatus.APPROVED)) {
            throw new AppException(ResponseCode.UNAUTHORIZED);
        }
    }

    // tạo chương và lưu xuống db luôn để lấy id cho các pages
    private Chapter insertChapter(Chapter chapter) {
        try {
            return chapterRepository.saveAndFlush(chapter);   // flush => INSERT chạy ngay
        } catch (DataIntegrityViolationException e) {
            // hai request cùng đăng một chapterIndex, unique (manga_id, chapter_index) chặn lại
            throw new AppException(ResponseCode.CHAPTER_INDEX_ALREADY_EXISTED);
        }
    }

    // copy files ảnh tmp sang folder chính thức là mangas/
    private List<String> copyPagesToFinalLocation(String mangaId, String chapterId, List<String> tmpKeys) {
        String prefix = StorageKeysUtils.chapterPrefix(mangaId, chapterId);

        // Tính trước toàn bộ key đích: 001.webp, 002.webp, ...
        List<String> destKeys = new ArrayList<>(tmpKeys.size());
        for (int i = 0; i < tmpKeys.size(); i++) {
            destKeys.add(prefix + String.format("%03d", i + 1) + "." + ImageProcessor.OUTPUT_EXTENSION);
        }

        // Đăng ký dọn rác TRƯỚC khi copy: transaction rollback ở bất kỳ bước nào cũng dọn
        deleteOnRollback(destKeys);

        try {
            for (int i = 0; i < tmpKeys.size(); i++) {
                // không xóa tmp vội: user retry được, job dọn tmp/ sẽ lo sau
                objectStorage.copyFile(tmpKeys.get(i), destKeys.get(i), false);
            }
        } catch (NoSuchKeyException e) {          // phải đứng trước S3Exception vì là lớp con của nó
            log.warn("File tmp không còn tồn tại (có thể đã bị dọn): {}", e.getMessage());
            throw new AppException(ResponseCode.FILE_INVALID);
        } catch (SdkClientException e) {          // lỗi phía client (mạng, timeout...)
            log.error("Không kết nối được R2 khi copy ảnh chương", e);
            throw new AppException(ResponseCode.STORAGE_SERVICE_UNAVAILABLE);
        } catch (S3Exception e) {                 // lỗi phía dịch vụ
            log.error("R2 trả lỗi khi copy ảnh chương", e);
            throw new AppException(ResponseCode.STORAGE_SERVICE_ERROR);
        }

        return destKeys.stream().map(objectStorage::toPublicUrl).toList();
    }

    // Chạy SAU khi transaction kết thúc. Nếu không phải COMMITTED thì xóa các file đã (hoặc định) copy
    private void deleteOnRollback(List<String> keys) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) return;

                try {
                    objectStorage.deleteFiles(keys);
                    log.warn("Transaction không commit, đã dọn {} file trên R2", keys.size());
                } catch (Exception e) {
                    // dọn lỗi cũng không được làm mất lỗi gốc
                    log.error("Không dọn được {} file rác trên R2, prefix {}", keys.size(), keys.getFirst(), e);
                }
            }
        });
    }

}