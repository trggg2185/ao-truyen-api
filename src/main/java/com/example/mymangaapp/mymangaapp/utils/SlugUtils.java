package com.example.mymangaapp.mymangaapp.utils;

import com.github.slugify.Slugify;

public final class SlugUtils {

    // Tạo một instance duy nhất, có thể tái sử dụng và thread-safe
    private static final Slugify SLUGIFY = Slugify.builder()
            .transliterator(true) // Bật transliteration cho tiếng Việt
            .build();

    private SlugUtils() {}

    public static String generateSlug(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }

        return SLUGIFY.slugify(input);
    }

    public static String generateSlug(String input, long number) {
        if (input == null || input.isBlank()) {
            return "";
        }

        return SLUGIFY.slugify(input) + "-" + number;
    }

}
