package com.example.mymangaapp.mymangaapp.utils;

import com.example.mymangaapp.mymangaapp.service.storage.ImageProcessor;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public final class StorageKeysUtils {

    private StorageKeysUtils() {}

    static String TMP_PREFIX = "tmp/";
    static String USER_PREFIX = "users/";
    static String GROUP_PREFIX = "groups/";
    static String MANGA_PREFIX = "mangas/";
    static String CHAPTER_PREFIX = "chapter/";

    static String AVATAR_PREFIX = "avatars/";
    static String COVER_IMAGE_PREFIX = "cover/";
    static DateTimeFormatter DATE_FOLDER =  DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // url r2 cho các file ảnh đc lưu trong tmp: tmp/yyyy-MM-dd/uuid.webp
    public static String tmp() {
        return TMP_PREFIX + LocalDateTime.now(ZoneId.systemDefault()).format(DATE_FOLDER) + "/" + newFileName();
    }

    // path r2 cho avatar của user: users/userId/avatars/uuid.webp
    public static String avatar(String userId) {
        return avatarPrefix(userId) + newFileName();
    }
    public static String avatarPrefix(String userId) {
        return userPrefix(userId) + AVATAR_PREFIX;
    }
    public static String userPrefix(String userId) { return USER_PREFIX + userId + "/"; }

    // path r2 cho avatar của nhóm dịch: groups/groupId/avatars/uuid.webp
    public static String groupAvatar(String groupId) { return groupAvatarPrefix(groupId) + newFileName(); }
    public static String groupAvatarPrefix(String groupId) {
        return GROUP_PREFIX + groupId + "/" + AVATAR_PREFIX;
    }

    // path r2 cho ảnh bìa của nhóm dịch: groups/groupId/cover/uuid.webp
    public static String groupCover(String groupId) {
        return groupCoverPrefix(groupId) + newFileName();
    }
    public static String groupCoverPrefix(String groupId) {
        return GROUP_PREFIX + groupId + "/" + COVER_IMAGE_PREFIX;
    }

    // path r2 cho ảnh bìa của truyện: mangas/mangaId/cover/uuid.webp
    public static String mangaCover(String mangaId) {
        return mangaCoverPrefix(mangaId) + newFileName();
    }
    public static String mangaCoverPrefix(String mangaId) {
        return mangaPrefix(mangaId) + COVER_IMAGE_PREFIX;
    }
    public static String mangaPrefix(String mangaId) { return MANGA_PREFIX + mangaId + "/"; }

    public static String chapterPrefix(String mangaId, String chapterId) {
        return MANGA_PREFIX + mangaId + "/" + CHAPTER_PREFIX + chapterId + "/";
    }

    public static boolean isTmp(String key) {
        return key.startsWith(TMP_PREFIX);
    }

    // tên file là uuid, đuôi file là webp
    public static String newFileName() {
        return UUID.randomUUID() + "." + ImageProcessor.OUTPUT_EXTENSION;
    }

}
