package com.example.mymangaapp.mymangaapp.service;

import java.util.List;

import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import com.example.mymangaapp.mymangaapp.dto.permission.request.PermissionRequest;
import com.example.mymangaapp.mymangaapp.dto.permission.response.PermissionResponse;
import com.example.mymangaapp.mymangaapp.entity.Permission;
import com.example.mymangaapp.mymangaapp.exception.AppException;
import com.example.mymangaapp.mymangaapp.exception.ResponseCode;
import com.example.mymangaapp.mymangaapp.mapper.PermissionMapper;
import com.example.mymangaapp.mymangaapp.repository.PermissionRepository;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class PermissionService {
    
    PermissionRepository permissionRepository;

    PermissionMapper permissionMapper;


    // -------------------------- chức năng của admin -------------------------- //

    // admin
    @Transactional
    public PermissionResponse createPermission(@NonNull PermissionRequest request) {

        if (permissionRepository.existsById(request.getName())) {
            throw new AppException(ResponseCode.PERMISSION_NAME_ALREADY_EXISTED);
        }

        Permission permission = permissionMapper.toPermission(request);

        return permissionMapper.toPermissionResponse(permissionRepository.save(permission));

    }

    // admin
    @Transactional(readOnly = true)
    public List<PermissionResponse> getAllPermissions() {

        List<Permission> permissions = permissionRepository.findAll();

        return permissions
                .stream()
                .map(permissionMapper::toPermissionResponse)
                .toList();
    }

    // admin
    @Transactional
    public void deletePermissionById(@NonNull String id) {

        if (!permissionRepository.existsById(id)) {
            throw new AppException(ResponseCode.PERMISSION_NOT_FOUND);
        }

        permissionRepository.deleteById(id);
    }

}
