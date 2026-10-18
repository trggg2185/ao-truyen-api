package com.example.mymangaapp.mymangaapp.dto.manga.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MangaSummaryResponse {

    String id;
    String name;
    String slug;
    String coverImageUrl;
    String authorsName;
    String status;
    String description;
    Instant createdAt;

}
