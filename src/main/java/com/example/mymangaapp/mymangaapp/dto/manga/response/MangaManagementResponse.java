package com.example.mymangaapp.mymangaapp.dto.manga.response;

import com.example.mymangaapp.mymangaapp.dto.category.response.CategoryResponse;
import com.example.mymangaapp.mymangaapp.dto.transgroup.response.TransGroupManagementResponse;
import com.example.mymangaapp.mymangaapp.enums.MangaStatus;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.List;
import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MangaManagementResponse {

    // Đây là response dành cho khi leader muốn xem chi tiết truyện để update

    String id;
    String name;
    String slug;
    String coverImageUrl;
    String authorsName;
    MangaStatus status;
    String description;
    Set<CategoryResponse> categories;
    List<TransGroupManagementResponse> transGroups;
    Instant updatedAt;

    // Constructor phục vụ DTO-based projection (MangaRepository.findManagementById).
    // categories và transGroups được nạp riêng bằng projection để tránh tích Đề-các (Cartesian product).
    public MangaManagementResponse(
            String id,
            String name,
            String slug,
            String coverImageUrl,
            String authorsName,
            MangaStatus status,
            String description,
            Instant updatedAt
    ) {
        this.id = id;
        this.name = name;
        this.slug = slug;
        this.coverImageUrl = coverImageUrl;
        this.authorsName = authorsName;
        this.status = status;
        this.description = description;
        this.updatedAt = updatedAt;
    }

}
