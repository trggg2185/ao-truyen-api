package com.example.mymangaapp.mymangaapp.dto.manga.response;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class DeleteMangaResponse {
    String mangaId;
}
