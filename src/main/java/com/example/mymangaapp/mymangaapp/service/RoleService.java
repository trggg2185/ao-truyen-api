package com.example.mymangaapp.mymangaapp.service;

import java.util.HashSet;
import java.util.List;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import com.example.mymangaapp.mymangaapp.dto.role.request.RoleRequest;
import com.example.mymangaapp.mymangaapp.dto.role.response.RoleResponse;
import com.example.mymangaapp.mymangaapp.entity.Permission;
import com.example.mymangaapp.mymangaapp.entity.Role;
import com.example.mymangaapp.mymangaapp.exception.AppException;
import com.example.mymangaapp.mymangaapp.exception.ResponseCode;
import com.example.mymangaapp.mymangaapp.mapper.RoleMapper;
import com.example.mymangaapp.mymangaapp.repository.PermissionRepository;
import com.example.mymangaapp.mymangaapp.repository.RoleRepository;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
@Transactional(readOnly = true)
public class RoleService {
    
    RoleRepository roleRepository;
    PermissionRepository permissionRepository;

    RoleMapper roleMapper;


    // ----------------------- chức năng cho admin ---------------------------- //

    // admin
    @Transactional
    public RoleResponse createRole(@NonNull RoleRequest request) {

        if (roleRepository.existsById(request.getName())) {
            throw new AppException(ResponseCode.ROLE_NAME_ALREADY_EXISTED);
        }

        if (CollectionUtils.isEmpty(request.getPermissions())) {
            throw new AppException(ResponseCode.PERMISSION_REQUIRED);
        }

        List<Permission> permissions = permissionRepository.findAllById(request.getPermissions());

        if (CollectionUtils.isEmpty(permissions)) {
            throw new AppException(ResponseCode.PERMISSION_INVALID);
        }

        Role role = roleMapper.toRole(request);
        role.setPermissions(new HashSet<>(permissions));

        return roleMapper.toRoleResponse(roleRepository.save(role));
    }

    // admin
    public List<RoleResponse> getAllRoles() {

        List<Role> roles = roleRepository.findAll();

        return roles.stream()
                .map(roleMapper::toRoleResponse)
                .toList();
    }

    // admin
    @Transactional
    public void deleteRoleById(@NonNull String id) {

        if (!roleRepository.existsById(id)) {
            throw new AppException(ResponseCode.ROLE_NOT_FOUND);
        }

        roleRepository.deleteById(id);
    }

}
