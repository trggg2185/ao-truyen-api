package com.example.mymangaapp.mymangaapp.security.jwt;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Setter @Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AuthTokens {

    String accessToken;
    long accessExpiresInSeconds;
    String refreshToken;

}
