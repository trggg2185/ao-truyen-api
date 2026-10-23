package com.example.mymangaapp.mymangaapp.dto.user.request;

import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.multipart.MultipartFile;

@Data
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class UserUpdateRequest {

    // Update info user không nên cho update password trong này
    // Nên để đổi password thành 1 endpoint riêng

    String facebook;
    String discord;

    @Size(max = 50, message = "DISPLAY_NAME_INVALID")
    String displayName;

    @Size(max = 300, message = "BIO_INVALID")
    String bio;

    // ta sẽ bind file từ form-data trong request vào field này
    MultipartFile avatar;

}
