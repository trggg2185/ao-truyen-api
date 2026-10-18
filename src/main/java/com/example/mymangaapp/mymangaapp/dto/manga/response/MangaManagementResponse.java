package com.example.mymangaapp.mymangaapp.dto.manga.response;

import com.example.mymangaapp.mymangaapp.dto.category.response.CategoryResponse;
import com.example.mymangaapp.mymangaapp.dto.transgroup.response.TransGroupManagementResponse;
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
    String status;
    String description;
    Set<CategoryResponse> categories;
    List<TransGroupManagementResponse> transGroups;
    Instant updatedAt;

}
