package com.example.mymangaapp.mymangaapp.mapper;

import com.example.mymangaapp.mymangaapp.dto.user.response.CurrentUserResponse;
import com.example.mymangaapp.mymangaapp.dto.user.response.PublicUserProfileResponse;
import com.example.mymangaapp.mymangaapp.dto.user.response.UserSummaryResponse;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.example.mymangaapp.mymangaapp.dto.user.request.UserUpdateRequest;
import com.example.mymangaapp.mymangaapp.dto.user.response.UserResponse;
import com.example.mymangaapp.mymangaapp.entity.User;

@Mapper(componentModel = "spring")
public interface UserMapper {

    /*
        - NullValuePropertyMappingStrategy.IGNORE: Dùng để MapStruct không ghi đè
          null lên các trường hợp lệ (fullName, phone, avatarUrl...).

        - @Mapping(target = "...", ignore = true): Dùng để khoá chặt các trường
          nhạy cảm (id, role, password, email...) không cho phép cập nhật tự động qua form update này.
    */

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "username", ignore = true)
    @Mapping(target = "avatar", ignore = true)
    @Mapping(target = "email", ignore = true)
    @Mapping(target = "transGroup", ignore = true)
    @Mapping(target = "transGroups", ignore = true)
    @Mapping(target = "roles", ignore = true)
    @Mapping(target = "userIdentityList", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    // Dùng NullValuePropertyMappingStrategy.IGNORE để nếu request field = null thì BỎ QUA không ghi đè
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateUserFromRequest(@MappingTarget User user, UserUpdateRequest request);

    UserResponse toUserResponse(User user);

    UserSummaryResponse toUserSummaryResponse(User user);

    CurrentUserResponse toCurrentUserResponse(User user);

    @Mapping(target = "transGroup", ignore = true)
    PublicUserProfileResponse toPublicUserProfileResponse(User user);

}
