package com.example.mymangaapp.mymangaapp.service.auth;

import com.example.mymangaapp.mymangaapp.dto.auth.request.AuthRequest;
import com.example.mymangaapp.mymangaapp.dto.auth.request.IntrospectRequest;
import com.example.mymangaapp.mymangaapp.dto.auth.response.IntrospectResponse;
import com.example.mymangaapp.mymangaapp.entity.User;
import com.example.mymangaapp.mymangaapp.enums.AuthProvider;
import com.example.mymangaapp.mymangaapp.exception.AppException;
import com.example.mymangaapp.mymangaapp.exception.ResponseCode;
import com.example.mymangaapp.mymangaapp.repository.UserRepository;
import com.example.mymangaapp.mymangaapp.security.jwt.AuthTokens;
import com.example.mymangaapp.mymangaapp.security.social.ExternalIdentity;
import com.example.mymangaapp.mymangaapp.security.social.SocialIdentityVerifierRegistry;
import com.example.mymangaapp.mymangaapp.security.utils.JwtUtils;
import com.example.mymangaapp.mymangaapp.service.InvalidatedTokenService;
import com.example.mymangaapp.mymangaapp.service.RefreshTokenService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class AuthService {

    SocialIdentityVerifierRegistry registry;

    SocialAccountService socialAccountService;
    InvalidatedTokenService invalidatedTokenService;
    RefreshTokenService refreshTokenService;
    TokenService tokenService;

    UserRepository userRepository;

    JwtUtils jwtUtils;

    /*
    *
    * Quyết định thiết kế cơ chế access và refresh jwt token
    *   1. Refresh token là chuỗi ngẫu nhiên, không phải JWT. Dạng này thu hồi bằng một lệnh xóa,
    *       không thể bị nhầm với access token (khác định dạng, khỏi cần claim typ), và bạn đã có Redis.
    *       JWT refresh vẫn cần trạng thái ở server mới thu hồi được, nên mất hết lợi thế “stateless” mà phải gánh phức tạp.
    *   2. Server chỉ lưu băm SHA-256 của refresh token, giống cách không lưu mật khẩu thô.
    *       Rò rỉ Redis thì kẻ xấu cũng không dùng được token. Token có 256 bit ngẫu nhiên nên dùng băm nhanh là đủ,
    *       không cần băm chậm như mật khẩu.
    *   3. Xoay vòng (rotation). Mỗi lần refresh, token cũ bị đánh dấu đã dùng và server cấp token mới.
    *       Nếu token cũ bị đưa ra lần nữa thì có người đang dùng bản sao bị đánh cắp, và server thu hồi cả phiên.
    *       Giống thu thẻ cũ khi cấp thẻ mới: ai đó đưa thẻ cũ ra thì báo động.
    *   4. Refresh token nằm trong cookie HttpOnly, access token giữ trong bộ nhớ trang (biến JavaScript),
    *       không để localStorage. Mã JavaScript, kể cả từ một lỗ hổng XSS, không đọc được cookie HttpOnly,
    *       nên thứ sống lâu nhất được bảo vệ tốt nhất.
    *
    * Đăng nhập (Google/Facebook)
    * -> server trả: access token (trong JSON) + refresh token (trong Set-Cookie, HttpOnly)
    *
    * Gọi API
    * -> Authorization: Bearer <access token>

    * Access token hết hạn (API trả 401)
    * -> frontend gọi POST /auth/refresh   (trình duyệt tự gửi cookie)
    * -> server: kiểm tra refresh token trong Redis, đánh dấu đã dùng, cấp cặp mới
    * -> frontend gọi lại request ban đầu với access token mới
    *
    * Tải lại trang (bộ nhớ trang bị xóa)
    * -> frontend gọi POST /auth/refresh ngay khi khởi động để lấy lại access token
    *
    * Đăng xuất
    * -> POST /auth/logout: thu hồi refresh token, đưa access token hiện tại vào blacklist, xóa cookie
    * */

    public AuthTokens login(AuthProvider provider, AuthRequest request) {

        // Đầu teên lấy provider từ registry rồi xác thực credential được gửi lên từ frontend
        // trả về các thông tin như provider, email, avatar, emailverified
        ExternalIdentity externalIdentity = registry.get(provider).verify(request.getCredential());

        // Lấy user id trong bảng user identity (danh tính ng dùng)
        // nếu là lần đâu tiên thì sẽ tạo người dùng, và thêm vào danh tính ng dùng
        String userId = socialAccountService.findOrCreateUserId(externalIdentity);

        User user = userRepository.findForAuthById(userId)
                .orElseThrow(() -> new AppException(ResponseCode.USER_NOT_FOUND));

        // trả về cặp (accesstoken, refreshtoken) và tgian hết hạn của accesstoken
        return tokenService.build(user, refreshTokenService.createSession(user.getId()));
    }

    // ---------------------------- chức năng public (cho khách) ----------------------------------//

    // Đôi với những hàm khác ngoài introspect sẽ coi việc xác thưc token trả về false
    // là 1 lỗi ứng dụng nên khi false sẽ ném ra ngoại lệ luôn



    // Check token hợp lệ
    public IntrospectResponse introspect(@NonNull IntrospectRequest request) {

        // introspect sẽ trả về kết quả true hoặc false cho token
        boolean valid = jwtUtils.verifyToken(request.getToken());

        return IntrospectResponse.builder()
                .valid(valid)
                .build();

    }

    // Đăng xuất (vô hiệu hoá token)
    public void logout(String rawRefreshToken, String accessToken) {

        refreshTokenService.revoke(rawRefreshToken);

        if (accessToken != null) {
            try {
                String jwtId = jwtUtils.extractJwtId(accessToken);
                long ttl = (jwtUtils.extractExpirationTime(accessToken).getTime() - System.currentTimeMillis()) / 1000;

                if (ttl > 0) invalidatedTokenService.invalidateToken(jwtId, ttl);
            } catch (Exception ignored) {

            }
        }

    }

    // Refresh token mới
    public AuthTokens refresh(String rawRefreshToken) {

        RefreshTokenService.Rotation rotation = refreshTokenService.rotate(rawRefreshToken);

        User user = userRepository.findForAuthById(rotation.userId())
                .orElseThrow(() -> new AppException(ResponseCode.UNAUTHENTICATED));

        return tokenService.build(user, rotation.newRefreshToken());
    }

}
