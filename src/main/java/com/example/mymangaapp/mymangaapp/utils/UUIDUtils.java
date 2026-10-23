package com.example.mymangaapp.mymangaapp.utils;

import java.util.UUID;

public final class UUIDUtils {

    private UUIDUtils() {}

    // check 1 chuỗi có phải uuid
    public static boolean isUuid(String value) {
        try {
            UUID.fromString(value);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

}
