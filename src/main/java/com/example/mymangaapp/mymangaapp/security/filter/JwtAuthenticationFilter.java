package com.example.mymangaapp.mymangaapp.security.filter;

import com.example.mymangaapp.mymangaapp.exception.AppException;
import com.example.mymangaapp.mymangaapp.exception.ResponseCode;
import com.example.mymangaapp.mymangaapp.security.service.CustomUserDetails;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.lang.NonNull;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.example.mymangaapp.mymangaapp.security.service.CustomUserDetailsService;
import com.example.mymangaapp.mymangaapp.security.utils.JwtUtils;
import org.slf4j.MDC;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

// OncePerRequestFilter là 1 filer trong filter chain
// Cứ mỗi http request đến là nó chạy 1 phát
// Để phục vụ cho việc authenticate jwt token xem có hợp lệ không
@Component
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
// Dù là public endpoint vẫn đi qua filter này nhé
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    JwtUtils jwtUtils;
    CustomUserDetailsService customUserDetailsService;

    // Dùng để đẩy Exception ở Filter về cho GlobalExceptionHandler
    // (@RestControllerAdvice) xử lý
    HandlerExceptionResolver resolver;

    public JwtAuthenticationFilter(
            JwtUtils jwtUtils,
            CustomUserDetailsService customUserDetailsService,
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.jwtUtils = jwtUtils;
        this.customUserDetailsService = customUserDetailsService;
        this.resolver = resolver;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) {

        try {
            String token = parseToken(request);

            // Check token ko null và có hợp lệ (chuẩn chữ ký, chưa hết hạn)
            // Nếu là public endpoint thì sẽ ko có header authorization 
            // nên token sẽ null và chạy qua luôn filter này, đền tầng authorization filter
            // vì ko có gì trong context đáng nhẽ bị chặn nhưng nó là public endpoint 
            // nên cứ thể qua tầng authorization filter
            if (token != null) {

                // Nếu TH có token nhưng lại không hợp lệ thì bắn unauthenticated luôn
                if (!jwtUtils.verifyToken(token)) {
                    throw new AppException(ResponseCode.UNAUTHENTICATED);
                }

                // Lấy username từ token
                String username = jwtUtils.extractUsername(token);

                if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    // Lấy thông tin UserDetails (bao gồm cả Authorities/Permissions) từ db
                    CustomUserDetails customUserDetails = customUserDetailsService.loadUserByUsername(username);

                    // Tạo đối tượng Authentication chuẩn của Spring Security
                    UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                            customUserDetails,
                            null, // Credentials để null vì đã xác thực qua Token
                            customUserDetails.getAuthorities() // Danh sách Role & Permission
                    );

                    // Đính kèm thêm thông tin phụ (IP, SessionId...) từ Request
                    authenticationToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                    // Nap authentiaction vao context
                    // request này chính thức đăng nhập thành công
                    SecurityContextHolder.getContext().setAuthentication(authenticationToken);
                    //MDC.put("userId", customUserDetails.getId());
                }
            }

            // Cho phép request đi tiếp tới các filter khác để đi vào controller
            filterChain.doFilter(request, response);

        } catch (Exception e) {
            // Nếu có lỗi Token hết hạn, sai chữ ký... bắn lỗi cho @RestControllerAdvice hứng
            resolver.resolveException(request, response, null, e);
        }

    }

    // Hàm phụ trợ: Bóc tách chuỗi "Bearer <TOKEN>" từ Header Authorization
    private String parseToken(HttpServletRequest request) {
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
