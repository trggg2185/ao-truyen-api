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
public class MangaDetailsResponse {

    // chi tiết info truyện khi user click vào truyện

    String id;
    String name;
    String slug;
    String coverImageUrl;
    String authorsName;
    String status;
    Set<CategoryResponse> categories;
    List<TransGroupSummaryResponse> transGroups;
    String description;
    Instant createdAt;

}
