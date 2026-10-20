package com.example.mymangaapp.mymangaapp.controller;

import com.example.mymangaapp.mymangaapp.annotation.RateLimit;
import com.example.mymangaapp.mymangaapp.dto.common.ApiResponse;
import com.example.mymangaapp.mymangaapp.dto.chapter.response.PageResponse;
import com.example.mymangaapp.mymangaapp.security.utils.SecurityUtils;
import com.example.mymangaapp.mymangaapp.service.ChapterViewService;
import com.example.mymangaapp.mymangaapp.service.PageService;
import com.example.mymangaapp.mymangaapp.utils.HttpUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PageController {

    PageService pageService;

    ChapterViewService chapterViewService;


    // -------------------------------endpoints public ----------------------------------//

    @GetMapping("/chapters/{chapterId}/pages")
    @RateLimit(capacity = 60)
    public ApiResponse<List<PageResponse>> getAllPagesByChapterId(
            @PathVariable @NonNull String chapterId,
            HttpServletRequest request,
            @RequestParam(defaultValue = "pageNumber") String sortBy // mặc định sort by số trang
    ) {

        List<PageResponse> responses = pageService.getAllPagesByChapterId(chapterId, sortBy);

        // Nếu giả tính view có lỗi thì ở hàm incrementView đã bắt exception và chỉ log ra lỗi thôi
        // chứ ko ném exception để khôgn ảnh hưởng tới việc đcọ truyện của user
        chapterViewService.incrementView(
                chapterId,
                SecurityUtils.findCurrentUserId().orElse(null),
                HttpUtils.getClientIp(request)
        );

        return ApiResponse.<List<PageResponse>>builder()
                .result(responses)
                .build();
    }
}
