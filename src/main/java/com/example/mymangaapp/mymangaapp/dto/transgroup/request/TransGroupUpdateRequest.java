package com.example.mymangaapp.mymangaapp.dto.transgroup.request;

import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.web.multipart.MultipartFile;

@Data
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class TransGroupUpdateRequest {

    @Size(min = 5, max = 50, message = "TRANSGROUP_NAME_INVALID")
    String name;

    @Size(max = 300, message = "TRANSGROUP_DESCRIPTION_INVALID")
    String description;

    // File avatar nhóm
    MultipartFile avatarFile;

    // File ảnh bìa nhóm
    MultipartFile coverFile;

}
