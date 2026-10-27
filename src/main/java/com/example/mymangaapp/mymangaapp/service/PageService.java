package com.example.mymangaapp.mymangaapp.service;

import com.example.mymangaapp.mymangaapp.dto.chapter.response.PageResponse;
import com.example.mymangaapp.mymangaapp.entity.Chapter;
import com.example.mymangaapp.mymangaapp.entity.Page;
import com.example.mymangaapp.mymangaapp.repository.PageRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class PageService {

    PageRepository pageRepository;

    // --------------------------------chức năng public --------------------------------//

    // Lấy tất cả các trang ảnh của chapter theo id (user đọc 1 chương truyện).
    // Dùng thẳng DTO-based Projection từ PageRepository, không cần PageMapper nữa.
    @Transactional(readOnly = true)
    public List<PageResponse> getAllPagesByChapterId(
            @NonNull String chapterId, @NonNull String sortBy
    ) {
        return pageRepository.findAllResponsesByChapterId(chapterId, Sort.by(Sort.Direction.ASC, sortBy));
    }

    // --------------------------------method cấp cho service khác dùng ----------------------------------//

    public List<Page> createPages(Chapter chapter, List<String> pageUrls) {
        return IntStream.range(0, pageUrls.size())
                .mapToObj(i -> (Page) Page.builder()
                        .pageNumber(i + 1)
                        .imageUrl(pageUrls.get(i))
                        .chapter(chapter)
                        .build())
                .toList();
    }

}
