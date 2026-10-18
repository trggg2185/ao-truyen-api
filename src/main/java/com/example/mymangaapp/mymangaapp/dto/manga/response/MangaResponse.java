package com.example.mymangaapp.mymangaapp.dto.manga.response;

import com.example.mymangaapp.mymangaapp.dto.category.response.CategoryResponse;
import com.example.mymangaapp.mymangaapp.dto.transgroup.response.TransGroupSummaryResponse;
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
public class MangaResponse {

    String id;
    String name;
    String slug;
    String coverImageUrl;
    String status;
    String description;
    Set<CategoryResponse> categories;
    List<TransGroupSummaryResponse> transGroups;
    Instant updatedAt;

}
