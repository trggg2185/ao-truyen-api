package com.example.mymangaapp.mymangaapp.mapper;

import com.example.mymangaapp.mymangaapp.dto.manga.request.MangaCreationRequest;
import com.example.mymangaapp.mymangaapp.dto.manga.request.MangaUpdateRequest;
import com.example.mymangaapp.mymangaapp.dto.manga.response.MangaDetailsResponse;
import com.example.mymangaapp.mymangaapp.dto.manga.response.MangaManagementResponse;
import com.example.mymangaapp.mymangaapp.dto.manga.response.MangaResponse;
import com.example.mymangaapp.mymangaapp.dto.manga.response.MangaSummaryResponse;
import com.example.mymangaapp.mymangaapp.entity.Manga;
import com.example.mymangaapp.mymangaapp.entity.TransGroup;
import org.mapstruct.*;

@Mapper(componentModel = "spring", uses = {CategoryMapper.class, ChapterMapper.class})
public interface MangaMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "transGroups", ignore = true)
    @Mapping(target = "categories", ignore = true)
    @Mapping(target = "chapters", ignore = true)
    @Mapping(target = "coverImageUrl", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    /*
    * NullValuePropertyMappingStrategy.IGNORE: Áp dụng cho TẤT CẢ các trường còn lại (những trường KHÔNG bị ignore),
    * có nghĩa là: Nếu trong MangaCreationRequest, trường nào đó có giá trị là null, thì ĐỪNG set null vào Manga,
    * hãy giữ nguyên giá trị mặc định của Manga."
    * */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    Manga toManga(MangaCreationRequest request);

    @Mapping(target = "categories", source = "categories")
    @Mapping(target = "transGroups", source = "transGroups")
    MangaResponse toMangaResponse(Manga manga);

    // Để mapper tự map từng phần tử transGroup thành transGroupId
    default String mapTransGroupToId(TransGroup transGroup) {
        return (transGroup != null) ? transGroup.getId() : null;
    }

    MangaSummaryResponse toMangaSummaryResponse(Manga manga);

    MangaDetailsResponse toMangaDetailsResponse(Manga manga);

    @Mapping(target = "transGroups", ignore = true) // tự tính
    MangaManagementResponse toMangaManagementResponse(Manga manga);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "categories", ignore = true)
    @Mapping(target = "transGroups", ignore = true)
    @Mapping(target = "chapters", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateMangaFromRequest(@MappingTarget Manga manga, MangaUpdateRequest request);

}
