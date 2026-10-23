package com.example.mymangaapp.mymangaapp.mapper;

import com.example.mymangaapp.mymangaapp.dto.groupjoin.response.JoinRequestResponse;
import com.example.mymangaapp.mymangaapp.entity.GroupJoinRequest;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface GroupJoinRequestMapper {

    // Không gán toàn bộ transgroup và user vào response, chỉ cần name là đủ
    JoinRequestResponse toJoinRequestResponse(GroupJoinRequest groupJoinRequest);
}
