package com.example.mymangaapp.mymangaapp.service;

import com.example.mymangaapp.mymangaapp.dto.chapter.request.ChapterRequest;
import com.example.mymangaapp.mymangaapp.dto.chapter.response.ChapterResponse;
import com.example.mymangaapp.mymangaapp.dto.chapter.response.ChapterSummaryResponse;
import com.example.mymangaapp.mymangaapp.dto.common.PaginatedResponse;
import com.example.mymangaapp.mymangaapp.entity.Chapter;
import com.example.mymangaapp.mymangaapp.entity.Page;
import com.example.mymangaapp.mymangaapp.exception.AppException;
import com.example.mymangaapp.mymangaapp.exception.ResponseCode;
import com.example.mymangaapp.mymangaapp.mapper.ChapterMapper;
import com.example.mymangaapp.mymangaapp.repository.ChapterRepository;
import com.example.mymangaapp.mymangaapp.repository.MangaRepository;
import com.example.mymangaapp.mymangaapp.service.storage.ImageProcessor;
import com.example.mymangaapp.mymangaapp.service.storage.StorageCleanup;
import com.example.mymangaapp.mymangaapp.service.storage.StorageService;
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
    StorageService storageService;

    StorageCleanup storageCleanup;

    // ------------------chức năng dành cho admin hoặc thành viên của các nhóm dịch truyện này------------------------//

    @PreAuthorize("@chapterSec.canMofifyChapter(#mangaId)")
    @Transactional
    public ChapterResponse createChapter(@NonNull String mangaId, @NonNull ChapterRequest request) {

        // Tồn tại + quyền
        if (!mangaRepository.existsById(mangaId)) {
            throw new AppException(ResponseCode.MANGA_NOT_FOUND);
        }

        // Kiểm tra nhanh để báo lỗi sớm (không bắt buộc, chỉ để đỡ tốn một lần insert)
        if (chapterRepository.existsByMangaIdAndChapterIndex(mangaId, request.getChapterIndex())) {
            throw new AppException(ResponseCode.CHAPTER_INDEX_ALREADY_EXISTED);
        }

        // Insert chapter NGAY để DB chặn trùng index trước khi tốn công copy ảnh
        Chapter chapter = chapterMapper.toChapter(request);
        chapter.setManga(mangaRepository.getReferenceById(mangaId));
        chapter = insertChapter(chapter);

        // Tính toán destKeys cho các trang truyện
        String prefix = StorageKeysUtils.chapterPrefix(mangaId, chapter.getId());
        List<String> destKeys = new ArrayList<>(request.getPageUrls().size());
        for (int i = 0; i < request.getPageUrls().size(); i++) {
            destKeys.add(prefix + String.format("%03d", i + 1) + "." + ImageProcessor.OUTPUT_EXTENSION);
        }

        // Copy ảnh từ tmp/ sang vị trí chính thức
        List<String> pageUrls = storageService.promoteTmpFiles(request.getPageUrls(), destKeys);

        // Tạo pages
        List<Page> pages = pageService.createPages(chapter, pageUrls);
        chapter.getPages().addAll(pages);

        log.info("Đã tạo chapter {} (index {}) với {} trang cho manga {}",
                chapter.getId(), chapter.getChapterIndex(), pages.size(), mangaId);

        return chapterMapper.toChapterResponse(chapter);
    }

    @PreAuthorize("@chapterSec.canMofifyChapter(#mangaId)")
    @Transactional
    public void deleteChapterById(@NonNull String mangaId, @NonNull String chapterId) {

        Chapter chapter = chapterRepository
                .findById(chapterId)
                .orElseThrow(() -> new AppException(ResponseCode.CHAPTER_NOT_FOUND));

        // Check chapter có thuộc về manga này ko
        if (!chapter.getManga().getId().equals(mangaId)) {
            throw new AppException(ResponseCode.UNAUTHORIZED);
        }

        // xoá chapter có cascade và orphanremoval nên page thuộc
        // về chapter cũng sẽ tự động xoá
        chapterRepository.delete(chapter);

        // Sau khi transaction commit xoá các chapter trong db thì xoá các files trong prefix trên r2
        storageCleanup.deletePrefixAfterCommit(StorageKeysUtils.chapterPrefix(mangaId, chapterId));
    }


    // ------------------------------------chức năng public (cho khách)-----------------------------------------//

    // user thì ai cx vào manga bất kỳ và đều đọc đc các chapters
    // ==> để public method này
    @Transactional(readOnly = true)
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


    // tạo chương và lưu xuống db luôn để lấy id cho các pages
    private Chapter insertChapter(Chapter chapter) {
        try {
            return chapterRepository.saveAndFlush(chapter);   // flush => INSERT chạy ngay
        } catch (DataIntegrityViolationException e) {
            // hai request cùng đăng một chapterIndex, unique (manga_id, chapter_index) chặn lại
            throw new AppException(ResponseCode.CHAPTER_INDEX_ALREADY_EXISTED);
        }
    }

}