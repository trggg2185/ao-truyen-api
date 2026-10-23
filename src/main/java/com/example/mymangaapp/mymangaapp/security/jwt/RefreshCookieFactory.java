package com.example.mymangaapp.mymangaapp.security.jwt;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RefreshCookieFactory {

    public static final String NAME = "refresh_token";

    @Value("${app.auth.refresh-ttl}")
    Duration refreshTtl;

    @Value("${app.auth.cookie.secure}")
    boolean secure;

    @Value("${app.auth.cookie.same-site}")
    String sameSite;

    @Value("${app.auth.cookie.path}")
    String path;

    public String create(String rawToken) {
        return ResponseCookie.from(NAME, rawToken)
                .httpOnly(true).secure(secure).sameSite(sameSite)
                .path(path).maxAge(refreshTtl).build().toString();
    }

    public String clear() {
        return ResponseCookie.from(NAME, "")
                .httpOnly(true).secure(secure).sameSite(sameSite)
                .path(path).maxAge(0).build().toString();
    }

}
