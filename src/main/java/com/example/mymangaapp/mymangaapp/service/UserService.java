package com.example.mymangaapp.mymangaapp.service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.example.mymangaapp.mymangaapp.constant.RoleConstants;
import com.example.mymangaapp.mymangaapp.dto.user.request.UserPasswordRequest;
import com.example.mymangaapp.mymangaapp.dto.common.PaginatedResponse;
import com.example.mymangaapp.mymangaapp.dto.user.response.CurrentUserResponse;
import com.example.mymangaapp.mymangaapp.dto.user.response.UserSummaryResponse;
import com.example.mymangaapp.mymangaapp.security.utils.SecurityUtils;
import com.example.mymangaapp.mymangaapp.service.storage.ObjectStorage;
import com.example.mymangaapp.mymangaapp.service.storage.StorageService;
import com.example.mymangaapp.mymangaapp.utils.StorageKeysUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.example.mymangaapp.mymangaapp.dto.user.request.UserCreationRequest;
import com.example.mymangaapp.mymangaapp.dto.user.request.UserUpdateRequest;
import com.example.mymangaapp.mymangaapp.dto.user.response.UserResponse;
import com.example.mymangaapp.mymangaapp.entity.Role;
import com.example.mymangaapp.mymangaapp.entity.User;
import com.example.mymangaapp.mymangaapp.exception.AppException;
import com.example.mymangaapp.mymangaapp.exception.ResponseCode;
import com.example.mymangaapp.mymangaapp.mapper.UserMapper;
import com.example.mymangaapp.mymangaapp.repository.RoleRepository;
import com.example.mymangaapp.mymangaapp.repository.UserRepository;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserService {

    UserRepository userRepository;
    RoleRepository roleRepository;

    PasswordEncoder passwordEncoder;

    // Map từ đối tượng này sang đối tượng khác nhanh chóng
    UserMapper userMapper;

    ObjectStorage objectStorage;

    StorageService storageService;

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

    @Transactional
    public void updateMyPassword(UserPasswordRequest request) {
        String username = SecurityUtils.getCurrentUsername();

        User user = userRepository
                .findByUsername(username)
                .orElseThrow(() -> new AppException(ResponseCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new AppException(ResponseCode.PASSWORD_INCORRECT);
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));

        userRepository.save(user);
    }


    // --------------------------------- chức năng admin đây ----------------------------------------- //

    // Tạo user mới dành cho admin
    @Transactional
    public UserResponse createUser(@NonNull UserCreationRequest request) {

        // Mặc dù username có unique vẫn phải check đã tồn tại ở service
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new AppException(ResponseCode.USERNAME_ALREADY_EXISTED);
        }

        // Email cũng phải check tương tự username
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new AppException(ResponseCode.EMAIL_ALREADY_EXISTED);
        }

        Set<Role> roles = new HashSet<>();

        if (request.getRoles().isEmpty()) {
            // Tìm role mặc định cho user mới là role USER
            Role role = roleRepository
                    .findById(RoleConstants.USER)
                    .orElseThrow(() ->
                            new AppException(ResponseCode.ROLE_NOT_FOUND));

            roles.add(role);
        } else {
            List<Role> foundRoles = roleRepository.findAllById(request.getRoles());
            roles.addAll(foundRoles);
        }

        User user = userMapper.toUser(request);

        // Mã hoá mật khẩu
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        // Set role mặc định cho user mới tạo
        user.setRoles(roles);

        log.info("Admin tạo user thành công!");
        return userMapper.toUserResponse(userRepository.save(user));

    }

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

        String newEmail = request.getEmail();

        // Request có chứa email khác null tức là email đã thay đổi
        // Phải kiểm tra email thay đổi đó có tồn tại trong db chưa
        // Nếu trong TH họ vẫn nhập email nhưng email mới và email cũ chả khác gì nhau thì thôi
        if (newEmail != null && !newEmail.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmail(newEmail)) {
            throw new AppException(ResponseCode.EMAIL_ALREADY_EXISTED);
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
