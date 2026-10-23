package com.example.mymangaapp.mymangaapp.dto.auth.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
public class AuthRequest {

    @NotBlank(message = "CREDENTIAL_REQUIRED")
    String credential;

}
