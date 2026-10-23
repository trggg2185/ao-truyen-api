package com.example.mymangaapp.mymangaapp.service;

import java.time.Instant;
import java.util.Optional;

import com.example.mymangaapp.mymangaapp.constant.RoleConstants;
import com.example.mymangaapp.mymangaapp.dto.common.PaginatedResponse;
import com.example.mymangaapp.mymangaapp.dto.user.response.CurrentUserResponse;
import com.example.mymangaapp.mymangaapp.dto.user.response.PublicUserProfileResponse;
import com.example.mymangaapp.mymangaapp.dto.user.response.UserSummaryResponse;
import com.example.mymangaapp.mymangaapp.entity.TransGroup;
import com.example.mymangaapp.mymangaapp.enums.TransGroupStatus;
import com.example.mymangaapp.mymangaapp.mapper.TransGroupMapper;
import com.example.mymangaapp.mymangaapp.repository.TransGroupRepository;
import com.example.mymangaapp.mymangaapp.security.utils.SecurityUtils;
import com.example.mymangaapp.mymangaapp.service.storage.ObjectStorage;
import com.example.mymangaapp.mymangaapp.service.storage.StorageService;
import com.example.mymangaapp.mymangaapp.utils.StorageKeysUtils;
import com.example.mymangaapp.mymangaapp.utils.UUIDUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import com.example.mymangaapp.mymangaapp.dto.user.request.UserUpdateRequest;
import com.example.mymangaapp.mymangaapp.dto.user.response.UserResponse;
import com.example.mymangaapp.mymangaapp.entity.User;
import com.example.mymangaapp.mymangaapp.exception.AppException;
import com.example.mymangaapp.mymangaapp.exception.ResponseCode;
import com.example.mymangaapp.mymangaapp.mapper.UserMapper;
import com.example.mymangaapp.mymangaapp.repository.UserRepository;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Transactional(readOnly = true)
public class UserService {

    UserRepository userRepository;
    TransGroupRepository transGroupRepository;

    // Map từ đối tượng này sang đối tượng khác nhanh chóng
    UserMapper userMapper;
    TransGroupMapper transGroupMapper;

    ObjectStorage objectStorage;

    StorageService storageService;


    // ---------------------------------- chức năng cho khách ---------------------------------------------------- //

    public PublicUserProfileResponse getUserProfile(@NonNull String idOrUsername) {

        User user = UUIDUtils.isUuid(idOrUsername)
                ? userRepository // là id thì tìm theo id
                        .findWithGroupById(idOrUsername)
                        .orElseThrow(() -> new AppException(ResponseCode.USER_NOT_FOUND))
                : userRepository // là username thì tìm theo username
                        .findWithGroupByUsername(idOrUsername)
                        .orElseThrow(() -> new AppException(ResponseCode.USER_NOT_FOUND));

        PublicUserProfileResponse response = userMapper.toPublicUserProfileResponse(user);

        TransGroup joinedGroup = user.getTransGroup();

        if (joinedGroup != null && joinedGroup.getStatus().equals(TransGroupStatus.APPROVED)) {
            response.setTransGroup(transGroupMapper.toTransGroupSummaryResponse(user.getTransGroup()));
        }

        Optional<TransGroup> userGroupOpt = transGroupRepository
                .findByLeaderIdAndStatus(user.getId(), TransGroupStatus.APPROVED);

        userGroupOpt.ifPresent(transGroup ->
                response.setTransGroup(transGroupMapper.toTransGroupSummaryResponse(transGroup)));

        return response;
    }


    // --------------------------------- chức năng cho user đã đăng nhập đây -----------------------------------------  //

    public CurrentUserResponse getMyInfo() {
        String username = SecurityUtils.getCurrentUsername();

        User user = userRepository
                .findByUsername(username)
                .orElseThrow(() -> new AppException(ResponseCode.USER_NOT_FOUND));

        return userMapper.toCurrentUserResponse(user);
    }

    @Transactional
    public CurrentUserResponse updateMyInfo(UserUpdateRequest request) {
        String username = SecurityUtils.getCurrentUsername();

        User user = userRepository
                .findByUsername(username)
                .orElseThrow(() -> new AppException(ResponseCode.USER_NOT_FOUND));

        userMapper.updateUserFromRequest(user, request);

        // nếu có cập nhật avatar cập nhật
        if (request.getAvatar() != null && !request.getAvatar().isEmpty()) {

            // nếu user có avatar cũ thì xoá trc đã, nếu ko có thì thôi upload avatar mới luôn rồi set
            if (user.getAvatar() != null && !user.getAvatar().isEmpty()) {
                String prefix =  StorageKeysUtils.avatarPrefix(user.getId());
                objectStorage.deleteFilesWithPrefix(prefix, Instant.now());
            }

            // sau mới upload avatar mới
            String avatarUrl = storageService.uploadAvatar(user.getId(), request.getAvatar());
            user.setAvatar(avatarUrl);

        }

        return userMapper.toCurrentUserResponse(userRepository.save(user));
    }


    // --------------------------------- chức năng admin đây ----------------------------------------- //

    // Lấy tất cả user
    public PaginatedResponse<UserSummaryResponse> getAllUsers(
            int page, int size,
            @NonNull String sortBy
    ) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, sortBy));

        // Chỉ cần trả về ds với response tối thiểu
        Page<UserSummaryResponse> dtoPage = userRepository
                .findAll(pageable).map(userMapper::toUserSummaryResponse);

        return PaginatedResponse.of(dtoPage);

    }

    // Lấy user bằng id
    public UserResponse getUserById(@NonNull String id) {
        User user = userRepository
                .findWithDetailsById(id)
                .orElseThrow(() -> 
                        new AppException(ResponseCode.USER_NOT_FOUND));

        return userMapper.toUserResponse(user);
    }

    // Cập nhật user bằng id
    @Transactional
    public UserResponse updateUserById(@NonNull String id, @NonNull UserUpdateRequest request) {
        
        User user = userRepository
                .findWithDetailsById(id) // phải lấy info chi tiết (roles, permissions, transgroup_id)
                .orElseThrow(() -> new AppException(ResponseCode.USER_NOT_FOUND));

        // Nếu user mà admin đang cập nhật cũng là admin thì ko cho phép
        if (userRepository.hasRole(id, RoleConstants.ADMIN)) {
            throw new AppException(ResponseCode.CANNOT_MODIFY_ADMIN);
        }

        // Mapstruct tự động map từ UserUpdateRequest -> User
        userMapper.updateUserFromRequest(user, request);

        // nếu request có avatar thì cập nhật
        if (request.getAvatar() != null && !request.getAvatar().isEmpty()) {

            // nếu user có avatar cũ thì xoá trc đã, nếu ko có thì thôi upload avatar mới luôn rồi set
            if (user.getAvatar() != null && !user.getAvatar().isEmpty()) {
                String prefix = StorageKeysUtils.avatarPrefix(user.getId());
                objectStorage.deleteFilesWithPrefix(prefix, Instant.now());
            }

            // sau mới upload avatar mới
            String avatarUrl = storageService.uploadAvatar(user.getId(), request.getAvatar());
            user.setAvatar(avatarUrl);

        }

        return userMapper.toUserResponse(userRepository.save(user));
    }


    // --------------------------- chức năng cho admin hoặc user -------------------------- //

    // Xoá user bằng id, admin xoá đc mọi user, user chỉ xoá đc chính mình
    @Transactional
    @PreAuthorize("@userSec.isSelfOrAdmin(#id)")
    public void deleteUserById(@NonNull String id) {

        if (!userRepository.existsById(id)) {
            throw new AppException(ResponseCode.USER_NOT_FOUND);
        }

        // Nếu user mà admin đang cập nhật cũng là admin thì ko cho phép
        if (userRepository.hasRole(id, RoleConstants.ADMIN)) {
            throw new AppException(ResponseCode.CANNOT_MODIFY_ADMIN);
        }

        userRepository.deleteById(id);
    }

}
