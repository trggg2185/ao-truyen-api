package com.example.mymangaapp.mymangaapp.controller;

import com.example.mymangaapp.mymangaapp.annotation.RateLimit;
import com.example.mymangaapp.mymangaapp.dto.auth.request.*;
import com.example.mymangaapp.mymangaapp.enums.AuthProvider;
import com.example.mymangaapp.mymangaapp.security.jwt.AuthTokens;
import com.example.mymangaapp.mymangaapp.security.jwt.RefreshCookieFactory;
import com.example.mymangaapp.mymangaapp.security.utils.JwtUtils;
import com.example.mymangaapp.mymangaapp.service.auth.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.*;

import com.example.mymangaapp.mymangaapp.dto.common.ApiResponse;
import com.example.mymangaapp.mymangaapp.dto.auth.response.AuthResponse;
import com.example.mymangaapp.mymangaapp.dto.auth.response.IntrospectResponse;
import com.example.mymangaapp.mymangaapp.enums.LimitType;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class AuthController {
    
    AuthService authService;

    RefreshCookieFactory refreshCookieFactory;

    JwtUtils jwtUtils;


    // ----------------------------endpoint public (cho khách) ----------------------------------//

    @RateLimit(capacity = 10)
    @PostMapping("/social/{provider}")
    public ResponseEntity<ApiResponse<AuthResponse>> socialLogin(
            @PathVariable @NonNull String provider,
            @RequestBody @Valid AuthRequest request
    ) {

        AuthTokens tokens = authService.login(AuthProvider.fromKey(provider), request);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.create(tokens.getRefreshToken()))
                .body(ApiResponse.<AuthResponse>builder()
                        .result(toResponse(tokens))
                        .build());
    }

    @PostMapping("/introspect")
    @RateLimit(capacity = 60)
    public ApiResponse<IntrospectResponse> introspect(@RequestBody @Valid IntrospectRequest request) {

        IntrospectResponse response = authService.introspect(request);

        return ApiResponse.<IntrospectResponse>builder()
                .result(response)
                .build();

    }

    @PostMapping("/logout")
    @RateLimit(capacity = 10, limitType = LimitType.USER_ID)
    public ResponseEntity<ApiResponse<Void>> logout(
            @CookieValue(name = RefreshCookieFactory.NAME, required = false) String refeshToken,
            HttpServletRequest request
    ) {

        authService.logout(refeshToken, jwtUtils.parseToken(request));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookieFactory.clear())
                .body(ApiResponse.<Void>builder().build());
    }

    @PostMapping("/refresh")
    @RateLimit(capacity = 10, limitType = LimitType.USER_ID)
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
            @CookieValue(name = RefreshCookieFactory.NAME, required = false) String refeshToken
    ) {

        AuthTokens tokens = authService.refresh(refeshToken);

        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (tokens.getRefreshToken() != null) {   // null ở khoảng ân hạn: giữ nguyên cookie hiện có
            response.header(HttpHeaders.SET_COOKIE, refreshCookieFactory.create(tokens.getRefreshToken()));
        }
        return response.body(ApiResponse.<AuthResponse>builder()
                .result(toResponse(tokens))
                .build());
    }


    // -------------------------private methods--------------------------------

    private AuthResponse toResponse(AuthTokens tokens) {

        log.info("Access token: {}", tokens.getAccessToken());
        log.info("Access expires in: {}", tokens.getAccessExpiresInSeconds());
        log.info("Refresh token: {}", tokens.getRefreshToken());

        return AuthResponse.builder()
                .authenticated(true)
                .token(tokens.getAccessToken())
                .expiresIn(tokens.getAccessExpiresInSeconds())
                .build();
    }

}
