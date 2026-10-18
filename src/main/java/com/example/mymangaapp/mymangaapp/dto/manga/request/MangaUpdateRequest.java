package com.example.mymangaapp.mymangaapp.dto.manga.request;

import com.example.mymangaapp.mymangaapp.enums.MangaStatus;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class MangaUpdateRequest {

    @Size(max = 200, message = "MANGA_NAME_INVALID")
    String name;

    @Size(max = 255, message = "AUTHORS_NAME_INVALID")
    String authorsName;

    @Size(max = 500, message = "MANGA_COVER_IMAGE_INVALID")
    String coverImageUrl;

    // chỉ chứa các mảng nhóm đã thêm, ko ghi đè lên các nhóm cũ
    // xoá nhóm dịch thì trong endpoints khác
    Set<String> addTransGroups;

    // Not blank dùng cho string, còn notempty dùng cho collection, set, list
    Set<Long> categoryIds;

    MangaStatus status;

    @Size(max = 500, message = "MANGA_DESCRIPTION_INVALID")
    String description;
}
