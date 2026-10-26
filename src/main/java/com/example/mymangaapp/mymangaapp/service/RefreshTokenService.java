package com.example.mymangaapp.mymangaapp.service;

import com.example.mymangaapp.mymangaapp.exception.AppException;
import com.example.mymangaapp.mymangaapp.exception.ResponseCode;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.NonFinal;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.*;

@Service @Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class RefreshTokenService {

    static String TOKEN_PREFIX = "mymangaapp:refresh:token:";
    static String FAMILY_PREFIX = "mymangaapp:refresh:family:";
    static String USER_PREFIX = "mymangaapp:refresh:user:";
    static SecureRandom RANDOM = new SecureRandom();

    StringRedisTemplate redisTemplate;
    DefaultRedisScript<String> refreshRotateScript;

    // refresh token không phải dạng jwt mà chỉ là 1 chuỗi ngẫu nhiên
    // và được lưu redis sau khi đã hash chứ ko lưu raw

    @NonFinal
    @Value("${app.auth.refresh-ttl}")
    Duration refreshTtl;

    @NonFinal
    @Value("${app.auth.refresh-grace}")
    Duration grace;

    // newRefreshToken = null khi rơi vào khoảng ân hạn (client giữ cookie hiện có)
    public record Rotation(String userId, String newRefreshToken) {}



    // Đăng nhập mới: mở một "họ" token mới, trả về refresh token thô (chỉ gửi cho client)
    public String createSession(String userId) {
        return create(userId, UUID.randomUUID().toString());
    }

    public Rotation rotate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new AppException(ResponseCode.UNAUTHENTICATED);
        }

        String result = redisTemplate.execute(
                refreshRotateScript,
                List.of(TOKEN_PREFIX + sha256(rawToken)),
                String.valueOf(grace.toSeconds())
        );

        if (result.equals("NONE")) {
            throw new AppException(ResponseCode.UNAUTHENTICATED);
        }

        String[] p = result.split("\\|");
        return switch (p[0]) {
            // refresh token chưa dùng thì đánh dấu là dùng và rồi trả refresh token mới
            case "OK" -> new Rotation(p[1], create(p[1], p[2]));

            case "GRACE" -> {
                // Check phiên còn sống ko (họ token còn trong redis ko)
                if (Boolean.FALSE.equals(redisTemplate.hasKey(FAMILY_PREFIX + p[2]))) {
                    throw new AppException(ResponseCode.UNAUTHENTICATED);
                }

                yield new Rotation(p[1], null);
            }
            // refresh bị dùng rồi mà gửi lại là nghi attack thì revoke cả họ
            default -> {
                log.warn("Refresh token bị dùng lại, thu hồi toàn bộ phiên, userId={}, familyId={}", p[1], p[2]);
                revokeFamily(p[2]);
                throw new AppException(ResponseCode.UNAUTHENTICATED);
            }
        };
    }

    // Đăng xuất thì thu hồi cả phiên của refresh token này
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) return;

        String tokenKey = TOKEN_PREFIX + sha256(rawToken);
        Object familyId = redisTemplate.opsForHash().get(tokenKey, "familyId");
        redisTemplate.delete(tokenKey);

        if (familyId != null) revokeFamily(familyId.toString());
    }

    // khi user tự xoá acc của mình thì revoke hết toàn bộ các họ token mà user đang giữ
    public void revokeAllForUser(String userId) {
        String key = USER_PREFIX + userId;

        // Lấy tất cả các họ mà user này đang giữ
        Set<String> families = redisTemplate.opsForSet().members(key);
        if (families != null) {
            families.forEach(this::revokeFamily); // revoke toàn bộ các họ mà user đang giữ
        }
        redisTemplate.delete(key); // rồi xoá luôn key này
    }

    // ------------------------private methods ---------------------------------

    // đăng nhập sẽ tạo mới 1 refresh token (lưu xuống redis sau khi encode bằng sha256) và 1 family id (là 1 UUID ngẫu nhiên)
    // và trả về cho client 1 refresh token dạng raw
    private String create(String userId, String familyId) {
        String raw = randomToken();
        String hash = sha256(raw);
        String tokenKey = TOKEN_PREFIX + hash;
        String familyKey = FAMILY_PREFIX + familyId;
        String userKey = USER_PREFIX + userId;

        // set refresh token key chứa user id và family id, set thời gian sống
        redisTemplate.opsForHash().putAll(tokenKey, Map.of("userId", userId, "familyId", familyId));
        redisTemplate.expire(tokenKey, refreshTtl);

        // set family key chứa hash của refresh token
        redisTemplate.opsForValue().set(familyKey, hash, refreshTtl);

        // Set thêm hiện tại user đang giữ họ token nào
        // cứ mỗi lần đăng nhập mới vào là thêm 1 family id vào
        // chỉ đến khi user xoá account của chính mình mới revoke toàn bộ, rồi xoá luôn key này
        redisTemplate.opsForSet().add(userKey, familyId);
        redisTemplate.expire(userKey, refreshTtl);
        return raw;
    }

    // thu hồi cả họ token
    private void revokeFamily(String familyId) {
        String familyKey = FAMILY_PREFIX + familyId;
        String currentHash = redisTemplate.opsForValue().get(familyKey);
        if (currentHash != null) {
            redisTemplate.delete(List.of(TOKEN_PREFIX + currentHash, familyKey));
        }
    }

    // tạo raw refresh token ngẫu nhiên
    private static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // dùng sha-256 để encode raw refresh token để lưu xuống redis
    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

}
