package com.example.mymangaapp.mymangaapp.dto.manga.request;

import com.example.mymangaapp.mymangaapp.enums.MangaStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Set;

@Data
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class MangaCreationRequest {

    /*
    * - Not Blank: cho string
    * - Not Empty: cho collection, set, list
    * - Not Null: cho object
    * */

    @NotBlank
    @Size(max = 200, message = "MANGA_NAME_INVALID")
    String name;

    @Size(max = 255, message = "AUTHORS_NAME_INVALID")
    String authorsName;

    // Ảnh bìa truyện ko bắt buộc
    // Nhưng frontend sẽ phải gọi endpoint upload lên tmp trước để lấy url
    // sau mới có thể đính kèm vào request tạo truyện được
    @Size(max = 500, message = "MANGA_COVER_IMAGE_INVALID")
    String coverImageUrl;

    // khi tạo manga, có thể hay không kèm theo những nhóm tham gia dịch
    // ko có thì mặc định trong service luôn có nhóm của ng đang tạo truyện
    Set<String> transGroups;

    // Not blank dùng cho string, còn notempty dùng cho collection, set, list
    @NotEmpty(message = "CATEGORIES_REQUIRED")
    Set<Long> categoryIds;

    @NotNull(message = "MANGA_STATUS_REQUIRED")
    MangaStatus status;

    @Size(max = 500, message = "MANGA_DESCRIPTION_INVALID")
    String description;

}
