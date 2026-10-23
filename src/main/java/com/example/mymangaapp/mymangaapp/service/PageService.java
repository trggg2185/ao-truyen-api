package com.example.mymangaapp.mymangaapp.service;

import com.example.mymangaapp.mymangaapp.dto.chapter.response.PageResponse;
import com.example.mymangaapp.mymangaapp.entity.Chapter;
import com.example.mymangaapp.mymangaapp.entity.Page;
import com.example.mymangaapp.mymangaapp.mapper.PageMapper;
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

    PageMapper pageMapper;


    // --------------------------------chức năng public --------------------------------//

    // lấy tất cả các files ảnh của chapter theo id (tức là user đọc 1 chương truyện)
    // ==> cx public luôn
    @Transactional(readOnly = true)
    public List<PageResponse> getAllPagesByChapterId(
            @NonNull String chapterId, @NonNull String sortBy
    ) {

        List<Page> pages = pageRepository.findAllByChapterId(chapterId, Sort.by(Sort.Direction.ASC, sortBy));

        return pages.stream()
                .map(pageMapper::toPageResponse)
                .toList();
    }


    // --------------------------------method cấp cho service khác dùng ----------------------------------//

    public List<Page> createPages(Chapter chapter, List<String> pageUrls) {

        // update method này dùng int stream biến đổi từ list page url
        // sang Page với int stream có luồng số nguyên từ 0 -> pagesUrls.size nên dễ gán pageNumber

        // Không save page, vì chapter entity dùng cascade
        // nên khi chapter service gọi hàm này rồi set cho nó
        // jpa auto save chapter và save cả các page này luôn
        return IntStream.range(0, pageUrls.size())
                .mapToObj(i -> (Page) Page.builder()
                        .pageNumber(i + 1)
                        .imageUrl(pageUrls.get(i))
                        .chapter(chapter)
                        .build())
                .toList();
    }

}
