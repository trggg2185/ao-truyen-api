package com.example.mymangaapp.mymangaapp.utils;

import com.example.mymangaapp.mymangaapp.exception.AppException;
import com.example.mymangaapp.mymangaapp.exception.ResponseCode;

public final class UsernameGenerator {

    private UsernameGenerator() {}

    // tách localpart của email làm username
    public static String fromEmail(String email) {
        if (email == null) return "";

        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            throw new AppException(ResponseCode.EMAIL_INVALID);
        }

        return email.substring(0, atIndex);
    }

}
