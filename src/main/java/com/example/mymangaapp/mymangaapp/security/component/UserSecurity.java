package com.example.mymangaapp.mymangaapp.security.component;

import com.example.mymangaapp.mymangaapp.security.utils.SecurityUtils;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component("userSec")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserSecurity {

    // Check chính mình hoặc admin
    public boolean isSelfOrAdmin(String userId) {
        if (SecurityUtils.isAdmin()) {
            return true;
        }

        return isSelf(userId);
    }

    public boolean isSelf(String userId) {
        String currentUserId = SecurityUtils.getCurrentUserId();
        return currentUserId.equals(userId);
    }

}
