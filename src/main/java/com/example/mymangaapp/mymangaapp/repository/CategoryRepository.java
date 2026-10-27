package com.example.mymangaapp.mymangaapp.repository;

import com.example.mymangaapp.mymangaapp.dto.category.response.CategoryResponse;
import com.example.mymangaapp.mymangaapp.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByName(String name);

    boolean existsByName(String name);

    /*
     * DTO-based Projection (Constructor Expression):
     * Thay vì gọi findAll() kéo toàn bộ Entity Category (bao gồm cả cột auditing createdAt, updatedAt
     * và quản lý trạng thái trong Persistence Context) rồi mới dùng MapStruct chuyển sang DTO,
     * câu query này yêu cầu Hibernate chỉ SELECT đúng 3 cột (id, name, description) và khởi tạo
     * trực tiếp đối tượng CategoryResponse ngay khi đọc ResultSet từ JDBC.
     */
    @Query("""
            SELECT new com.example.mymangaapp.mymangaapp.dto.category.response.CategoryResponse(
                c.id, c.name, c.description
            )
            FROM Category c
            ORDER BY c.name ASC
    """)
    List<CategoryResponse> findAllResponses();

    // DTO-based Projection cho truy vấn chi tiết 1 Category theo id
    @Query("""
            SELECT new com.example.mymangaapp.mymangaapp.dto.category.response.CategoryResponse(
                c.id, c.name, c.description
            )
            FROM Category c
            WHERE c.id = :id
    """)
    Optional<CategoryResponse> findResponseById(@Param("id") Long id);

}
