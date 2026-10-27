package com.example.mymangaapp.mymangaapp.mapper;

import com.example.mymangaapp.mymangaapp.dto.chapter.request.ChapterRequest;
import com.example.mymangaapp.mymangaapp.dto.chapter.response.ChapterResponse;
import com.example.mymangaapp.mymangaapp.entity.Chapter;
import com.example.mymangaapp.mymangaapp.entity.Page;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ChapterMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "manga", ignore = true)
    @Mapping(target = "views", ignore = true)
    @Mapping(target = "pages", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Chapter toChapter(ChapterRequest request);

    @Mapping(target = "mangaName", source = "manga.name")
    @Mapping(target = "pageUrls", source = "pages")
    ChapterResponse toChapterResponse(Chapter chapter);

    default String mapPageToUrl(Page page) {
        if (page != null)
            return page.getImageUrl();
        return null;
    }

}
