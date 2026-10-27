package com.example.mymangaapp.mymangaapp.service;

import com.example.mymangaapp.mymangaapp.constant.RoleConstants;
import com.example.mymangaapp.mymangaapp.dto.common.PaginatedResponse;
import com.example.mymangaapp.mymangaapp.dto.user.request.UserUpdateRequest;
import com.example.mymangaapp.mymangaapp.dto.user.response.CurrentUserResponse;
import com.example.mymangaapp.mymangaapp.dto.user.response.PublicUserProfileResponse;
import com.example.mymangaapp.mymangaapp.dto.user.response.UserResponse;
import com.example.mymangaapp.mymangaapp.dto.user.response.UserSummaryResponse;
import com.example.mymangaapp.mymangaapp.entity.User;
import com.example.mymangaapp.mymangaapp.enums.TransGroupStatus;
import com.example.mymangaapp.mymangaapp.enums.UserStatus;
import com.example.mymangaapp.mymangaapp.exception.AppException;
import com.example.mymangaapp.mymangaapp.exception.ResponseCode;
import com.example.mymangaapp.mymangaapp.mapper.UserMapper;
import com.example.mymangaapp.mymangaapp.repository.TransGroupRepository;
import com.example.mymangaapp.mymangaapp.repository.UserIdentityRepository;
import com.example.mymangaapp.mymangaapp.repository.UserRepository;
import com.example.mymangaapp.mymangaapp.security.utils.SecurityUtils;
import com.example.mymangaapp.mymangaapp.service.storage.ObjectStorage;
import com.example.mymangaapp.mymangaapp.service.storage.StorageCleanup;
import com.example.mymangaapp.mymangaapp.service.storage.StorageService;
import com.example.mymangaapp.mymangaapp.utils.StorageKeysUtils;
import com.example.mymangaapp.mymangaapp.utils.UUIDUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Transactional(readOnly = true)
public class UserService {

    UserRepository userRepository;
    TransGroupRepository transGroupRepository;
    UserIdentityRepository userIdentityRepository;

    UserMapper userMapper;

    ObjectStorage objectStorage;
    StorageService storageService;
    RefreshTokenService refreshTokenService;
    StorageCleanup storageCleanup;

    // ---------------------------------- chức năng cho khách ---------------------------------------------------- //

    /*
     * Xem trang cá nhân công khai bằng DTO-based Projection:
     * - Câu query findPublicProfileById / ByUsername dùng LEFT JOIN lấy luôn thông tin nhóm dịch
     *   nếu user là thành viên (member) của 1 nhóm APPROVED.
     * - Chỉ khi user chưa có nhóm ở vai trò member (response.getTransGroup() == null), ta mới gọi
     *   thêm findSummaryByLeaderIdAndStatus để kiểm tra xem user có đang làm leader của nhóm nào không.
     */
    public PublicUserProfileResponse getUserProfile(@NonNull String idOrUsername) {
        PublicUserProfileResponse response = UUIDUtils.isUuid(idOrUsername)
                ? userRepository
                        .findPublicProfileById(idOrUsername)
                        .orElseThrow(() -> new AppException(ResponseCode.USER_NOT_FOUND))
                : userRepository
                        .findPublicProfileByUsername(idOrUsername)
                        .orElseThrow(() -> new AppException(ResponseCode.USER_NOT_FOUND));

        if (response.getTransGroup() == null) {
            transGroupRepository
                    .findSummaryByLeaderIdAndStatus(response.getId(), TransGroupStatus.APPROVED)
                    .ifPresent(response::setTransGroup);
        }

        return response;
    }

    // --------------------------------- chức năng cho user đã đăng nhập đây ----------------------------------------- //

    // Dùng DTO-based Projection đọc thẳng ra CurrentUserResponse
    public CurrentUserResponse getMyInfo() {
        String username = SecurityUtils.getCurrentUsername();
        return userRepository
                .findCurrentUserByUsername(username)
                .orElseThrow(() -> new AppException(ResponseCode.USER_NOT_FOUND));
    }

    @Transactional
    public CurrentUserResponse updateMyInfo(UserUpdateRequest request) {
        String username = SecurityUtils.getCurrentUsername();

        User user = userRepository
                .findByUsername(username)
                .orElseThrow(() -> new AppException(ResponseCode.USER_NOT_FOUND));

        userMapper.updateUserFromRequest(user, request);

        if (request.getAvatar() != null && !request.getAvatar().isEmpty()) {
            if (user.getAvatar() != null && !user.getAvatar().isEmpty()) {
                String prefix = StorageKeysUtils.avatarPrefix(user.getId());
                objectStorage.deleteFilesWithPrefix(prefix, Instant.MAX);
            }

            String avatarUrl = storageService.uploadAvatar(user.getId(), request.getAvatar());
            user.setAvatar(avatarUrl);
        }

        return userMapper.toCurrentUserResponse(userRepository.save(user));
    }

    @Transactional
    public void deleteMyAccount() {
        String currentUserId = SecurityUtils.getCurrentUserId();

        User user = userRepository
                .findWithRolesById(currentUserId)
                .orElseThrow(() -> new AppException(ResponseCode.USER_NOT_FOUND));

        if (userRepository.hasRole(currentUserId, RoleConstants.ADMIN)) {
            throw new AppException(ResponseCode.CANNOT_MODIFY_ADMIN);
        }

        if (transGroupRepository.existsByLeaderIdAndStatus(currentUserId, TransGroupStatus.APPROVED)) {
            throw new AppException(ResponseCode.USER_IS_GROUP_LEADER);
        }

        user.setStatus(UserStatus.DELETED);
        user.setUsername("deleted_" + currentUserId.substring(0, 8));
        user.setDisplayName(null);
        user.setEmail(null);
        user.setBio(null);
        user.setFacebook(null);
        user.setDiscord(null);
        user.setAvatar(null);
        user.setTransGroup(null);
        user.getRoles().clear();

        userIdentityRepository.deleteAllByUserId(currentUserId);
        refreshTokenService.revokeAllForUser(currentUserId);
        storageCleanup.deletePrefixAfterCommit(StorageKeysUtils.userPrefix(currentUserId));
    }

    // --------------------------------- chức năng admin đây ----------------------------------------- //

    // Lấy danh sách user cho admin bằng DTO-based Projection (chỉ SELECT 5 cột vào UserSummaryResponse)
    public PaginatedResponse<UserSummaryResponse> getAllUsers(
            int page, int size,
            @NonNull String sortBy
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, sortBy));

        Page<UserSummaryResponse> dtoPage = userRepository.findAllSummaries(pageable);

        return PaginatedResponse.of(dtoPage);
    }

    public UserResponse getUserById(@NonNull String id) {
        User user = userRepository
                .findWithDetailsById(id)
                .orElseThrow(() -> new AppException(ResponseCode.USER_NOT_FOUND));

        return userMapper.toUserResponse(user);
    }

    @Transactional
    public UserResponse updateUserById(@NonNull String id, @NonNull UserUpdateRequest request) {
        User user = userRepository
                .findWithDetailsById(id)
                .orElseThrow(() -> new AppException(ResponseCode.USER_NOT_FOUND));

        if (userRepository.hasRole(id, RoleConstants.ADMIN)) {
            throw new AppException(ResponseCode.CANNOT_MODIFY_ADMIN);
        }

        userMapper.updateUserFromRequest(user, request);

        if (request.getAvatar() != null && !request.getAvatar().isEmpty()) {
            if (user.getAvatar() != null && !user.getAvatar().isEmpty()) {
                String prefix = StorageKeysUtils.avatarPrefix(user.getId());
                objectStorage.deleteFilesWithPrefix(prefix, Instant.MAX);
            }

            String avatarUrl = storageService.uploadAvatar(user.getId(), request.getAvatar());
            user.setAvatar(avatarUrl);
        }

        return userMapper.toUserResponse(userRepository.save(user));
    }

    @Transactional
    public void banUser(String userId) {
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new AppException(ResponseCode.USER_NOT_FOUND));

        if (userRepository.hasRole(userId, RoleConstants.ADMIN)) {
            throw new AppException(ResponseCode.CANNOT_MODIFY_ADMIN);
        }

        user.setStatus(UserStatus.BANNED);
        refreshTokenService.revokeAllForUser(userId);
    }

    @Transactional
    public void unbanUser(String userId) {
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new AppException(ResponseCode.USER_NOT_FOUND));

        if (userRepository.hasRole(userId, RoleConstants.ADMIN)) {
            throw new AppException(ResponseCode.CANNOT_MODIFY_ADMIN);
        }

        user.setStatus(UserStatus.ACTIVE);
    }

}
