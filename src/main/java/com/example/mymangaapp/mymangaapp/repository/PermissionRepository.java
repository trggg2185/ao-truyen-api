package com.example.mymangaapp.mymangaapp.repository;

import com.example.mymangaapp.mymangaapp.dto.permission.response.PermissionResponse;
import com.example.mymangaapp.mymangaapp.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface PermissionRepository extends JpaRepository<Permission, String> {

    // DTO-based Projection: chỉ lấy 2 cột (name, description) đóng gói thẳng vào PermissionResponse
    @Query("""
            SELECT new com.example.mymangaapp.mymangaapp.dto.permission.response.PermissionResponse(
                p.name, p.description
            )
            FROM Permission p
    """)
    List<PermissionResponse> findAllResponses();

}
