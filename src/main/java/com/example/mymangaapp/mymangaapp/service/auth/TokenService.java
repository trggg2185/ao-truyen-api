package com.example.mymangaapp.mymangaapp.service.auth;

import com.example.mymangaapp.mymangaapp.entity.User;
import com.example.mymangaapp.mymangaapp.security.jwt.AuthTokens;
import com.example.mymangaapp.mymangaapp.security.utils.JwtUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class TokenService {

    JwtUtils jwtUtils;

    @NonFinal
    @Value("${app.auth.access-ttl}")
    Duration accessTtl;

    public AuthTokens build(User user, String refreshToken) {
        return AuthTokens.builder()
                .accessToken(jwtUtils.generateAccessToken(user))
                .accessExpiresInSeconds(accessTtl.toSeconds())
                .refreshToken(refreshToken)
                .build();
    }

}
