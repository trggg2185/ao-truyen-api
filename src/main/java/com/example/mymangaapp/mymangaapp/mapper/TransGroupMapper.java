package com.example.mymangaapp.mymangaapp.mapper;

import com.example.mymangaapp.mymangaapp.dto.transgroup.request.TransGroupUpdateRequest;
import com.example.mymangaapp.mymangaapp.dto.transgroup.response.*;
import com.example.mymangaapp.mymangaapp.entity.TransGroup;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface TransGroupMapper {

    TransGroupResponse toTransGroupResponse(TransGroup transGroup);

    TransGroupSummaryResponse toTransGroupSummaryResponse(TransGroup transGroup);

    @Mapping(target = "translatedMangas", ignore = true)
    TransGroupDetailsResponse toTransGroupDetailsResponse(TransGroup transGroup);

    @Mapping(target = "canRemove", ignore = true)
    TransGroupManagementResponse toTransGroupManagementResponse(TransGroup transGroup);

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
