package com.example.mymangaapp.mymangaapp.mapper;

import com.example.mymangaapp.mymangaapp.dto.manga.request.MangaCreationRequest;
import com.example.mymangaapp.mymangaapp.dto.manga.request.MangaUpdateRequest;
import com.example.mymangaapp.mymangaapp.dto.manga.response.MangaResponse;
import com.example.mymangaapp.mymangaapp.entity.Manga;
import org.mapstruct.*;

@Mapper(componentModel = "spring", uses = { CategoryMapper.class })
public interface MangaMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "transGroups", ignore = true)
    @Mapping(target = "categories", ignore = true)
    @Mapping(target = "chapters", ignore = true)
    @Mapping(target = "coverImageUrl", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "sequenceNumber", ignore = true)
    @Mapping(target = "latestChapterAt", ignore = true)
    @Mapping(target = "latestChapterIndex", ignore = true)
    @Mapping(target = "totalViews", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    Manga toManga(MangaCreationRequest request);

    // Dùng khi trả về kết quả ngay sau khi createManga / updateMangaById
    @Mapping(target = "categories", source = "categories")
    @Mapping(target = "transGroups", source = "transGroups")
    MangaResponse toMangaResponse(Manga manga);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "categories", ignore = true)
    @Mapping(target = "transGroups", ignore = true)
    @Mapping(target = "chapters", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "sequenceNumber", ignore = true)
    @Mapping(target = "latestChapterAt", ignore = true)
    @Mapping(target = "latestChapterIndex", ignore = true)
    @Mapping(target = "totalViews", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateMangaFromRequest(@MappingTarget Manga manga, MangaUpdateRequest request);

}
