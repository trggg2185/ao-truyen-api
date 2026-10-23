package com.example.mymangaapp.mymangaapp.security.utils;

import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.StringJoiner;
import java.util.UUID;

import com.example.mymangaapp.mymangaapp.service.InvalidatedTokenService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import com.example.mymangaapp.mymangaapp.entity.User;
import com.example.mymangaapp.mymangaapp.exception.AppException;
import com.example.mymangaapp.mymangaapp.exception.ResponseCode;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

// Class tiện ích, cung cấp các hàm làm việc nhanh với jwt
// VD: tạo accesstoken, verify token
@FieldDefaults(level = AccessLevel.PRIVATE)
@RequiredArgsConstructor
@Component
@Slf4j
public class JwtUtils {

    @Value("${app.auth.signer-key}")
    String signerKey;

    @Value("${app.auth.access-ttl}")
    Duration accessTtl;

    final InvalidatedTokenService invalidatedTokenService;

    // tạo access token
    public String generateAccessToken(User user) {

        JWSHeader header = new JWSHeader(JWSAlgorithm.HS512);

        Instant now = Instant.now();
        Instant expirationTime = now.plusSeconds(accessTtl.toSeconds());

        JWTClaimsSet jwtClaimsSet = new JWTClaimsSet.Builder()
                .subject(user.getId()) // sửa lại subject chứa id thay vì username
                .issuer("trg2k5.com")
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expirationTime))
                .jwtID(UUID.randomUUID().toString())
                .claim("scope", buildScope(user))
                .build();

        Payload payload = new Payload(jwtClaimsSet.toJSONObject());

        JWSObject jwsObject = new JWSObject(header, payload);
        try {
            jwsObject.sign(new MACSigner(signerKey.getBytes()));
            return jwsObject.serialize();
        } catch (JOSEException e) {
            log.error("Không thể tạo access token do JOSEException!", e);
            throw new RuntimeException(e);
        }
        
    }

    // Lấy các claims từ token
    public JWTClaimsSet getClaimsSet(String token) {
       try {
            SignedJWT signedJWT = SignedJWT.parse(token);

            return signedJWT.getJWTClaimsSet();
       } catch (ParseException e) {
            throw new AppException(ResponseCode.UNAUTHENTICATED);
       }
    }

    // Lấy user id từ token
    public String extractUserId(String token) {
        return getClaimsSet(token).getSubject();
    }

    // Lấy jwt id từ token
    public String extractJwtId(String token) {
        return getClaimsSet(token).getJWTID();
    }

    // Lấy thời gian hết hạn từ token
    public Date extractExpirationTime(String token) {
        return getClaimsSet(token).getExpirationTime();
    }
 
    public boolean verifyToken(String token) {
        try {
            JWSVerifier verifier = new MACVerifier(signerKey.getBytes());
        
            SignedJWT signedJWT = SignedJWT.parse(token);

            // Check token bị vô hiệu hoá trong redis không
            if (invalidatedTokenService.isTokenInvalidated(signedJWT.getJWTClaimsSet().getJWTID())) {
                log.error("Token đã bị vô hiệu hoá!");
                return false;
            }

            Date exprirationTime = signedJWT.getJWTClaimsSet().getExpirationTime();

            return signedJWT.verify(verifier) && exprirationTime.after(new Date());

        } catch (JOSEException | ParseException e) {
            log.error("Xác thực token thất bại! Hoặc token không hợp lệ");
            return false;
        }
    }

    // scope chỉ cần chứa role của user là đủ
    public String buildScope(User user) {
        StringJoiner stringJoiner = new StringJoiner(" ");

        if (!CollectionUtils.isEmpty(user.getRoles())) {

            user.getRoles().forEach(role -> stringJoiner.add(role.getName()));

        }

        return stringJoiner.toString();
    }

    // Hàm phụ trợ: Bóc tách chuỗi "Bearer <TOKEN>" từ Header Authorization
    public String parseToken(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");

        // Lấy chuỗi sau 7 chữ cái đầu Bearer + space, chính là jwt token
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            String token = headerAuth.substring(7).trim();

            // Tránh TH trả về chuỗi rỗng
            return StringUtils.hasText(token) ? token : null;
        }

        return null;
    }

}
