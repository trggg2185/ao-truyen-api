package com.example.mymangaapp.mymangaapp.enums;

import com.example.mymangaapp.mymangaapp.exception.AppException;
import com.example.mymangaapp.mymangaapp.exception.ResponseCode;

import java.util.Arrays;

public enum AuthProvider {

    // Enum xác định các loại phương thức đăng nhập

    GOOGLE, FACEBOOK, DISCORD;

    public static AuthProvider fromKey(String key) {
        return Arrays.stream(values())
                .filter(p -> p.name().equalsIgnoreCase(key))
                .findFirst()
                .orElseThrow(() -> new AppException(ResponseCode.AUTH_PROVIDER_NOT_SUPPORTED));
    }

}
