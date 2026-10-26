package com.example.mymangaapp.mymangaapp.dto.chapter.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class PageResponse {

    String id;
    Integer pageNumber;
    String imageUrl;

}
