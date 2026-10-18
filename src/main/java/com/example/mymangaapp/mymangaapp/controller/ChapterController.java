package com.example.mymangaapp.mymangaapp.controller;

import com.example.mymangaapp.mymangaapp.annotation.RateLimit;
import com.example.mymangaapp.mymangaapp.enums.LimitType;
import com.example.mymangaapp.mymangaapp.dto.chapter.request.ChapterRequest;
import com.example.mymangaapp.mymangaapp.dto.common.ApiResponse;
import com.example.mymangaapp.mymangaapp.dto.chapter.response.ChapterResponse;
import com.example.mymangaapp.mymangaapp.dto.chapter.response.ChapterSummaryResponse;
import com.example.mymangaapp.mymangaapp.dto.chapter.response.DeleteChapterResponse;
import com.example.mymangaapp.mymangaapp.dto.common.PaginatedResponse;
import com.example.mymangaapp.mymangaapp.exception.ResponseCode;
import com.example.mymangaapp.mymangaapp.service.ChapterService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ChapterController {

    ChapterService chapterService;


    // ------------------endpoints dành cho translator hoặc admin nhưng vẫn phải là thành viên của nhóm mới được------------------------//

    @PostMapping("/mangas/{mangaId}/chapters")
    @RateLimit(capacity = 10, limitType = LimitType.USER_ID)
    public ApiResponse<ChapterResponse> createChapter(
            @PathVariable @NonNull String mangaId,
            @RequestBody @Valid ChapterRequest request
    ) {

        ChapterResponse response = chapterService.createChapter(mangaId, request);

        return ApiResponse.<ChapterResponse>builder()
                .result(response)
                .build();
    }

    // Mặc dù tên hàm là xoá theo id chapter nhưng ko phải nhóm nào cũng xoá đc
    // chỉ có chapter thuộc về manga của nhóm đó mới xoá đc nhé
    @DeleteMapping("/mangas/{mangaId}/chapters/{chapterId}")
    @RateLimit(capacity = 10, limitType = LimitType.USER_ID)
    public ApiResponse<DeleteChapterResponse> deleteChapterById(
            @PathVariable @NonNull String mangaId,
            @PathVariable @NonNull String chapterId
    ) {

        chapterService.deleteChapterById(mangaId, chapterId);

        return ApiResponse.<DeleteChapterResponse>builder()
                .message(ResponseCode.SUCCESS.getMessage())
                .result(DeleteChapterResponse.builder().chapterId(chapterId).mangaId(mangaId).build())
                .build();

    }


    // ----------------------------------endpoints public ---------------------------------------------//

    @GetMapping("/mangas/{mangaId}/chapters")
    @RateLimit(capacity = 60)
    public ApiResponse<PaginatedResponse<ChapterSummaryResponse>> getChaptersByMangaId(
            @PathVariable @NonNull String mangaId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            // Lấy ds các chapter của 1 manga mặc định xếp theo chapter index giảm dần
            @RequestParam(defaultValue = "chapterIndex") String sortBy
    ) {

        PaginatedResponse<ChapterSummaryResponse> responses = chapterService.getAllChaptersByMangaId(mangaId, page, size, sortBy);

        return ApiResponse.<PaginatedResponse<ChapterSummaryResponse>>builder()
                .result(responses)
                .build();
    }

}
