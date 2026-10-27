package com.example.mymangaapp.mymangaapp.mapper;

import com.example.mymangaapp.mymangaapp.dto.transgroup.request.TransGroupUpdateRequest;
import com.example.mymangaapp.mymangaapp.dto.transgroup.response.TransGroupResponse;
import com.example.mymangaapp.mymangaapp.dto.transgroup.response.TransGroupSummaryResponse;
import com.example.mymangaapp.mymangaapp.entity.TransGroup;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring")
public interface TransGroupMapper {

    TransGroupResponse toTransGroupResponse(TransGroup transGroup);

    TransGroupSummaryResponse toTransGroupSummaryResponse(TransGroup transGroup);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "leader", ignore = true)
    @Mapping(target = "members", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "translatedMangas", ignore = true)
    @Mapping(target = "avatar", ignore = true)
    @Mapping(target = "coverImageUrl", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateTransGroupFromRequest(@MappingTarget TransGroup transGroup, TransGroupUpdateRequest request);

}
