package com.example.mymangaapp.mymangaapp.security.filter;

import com.example.mymangaapp.mymangaapp.security.service.CustomUserDetails;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.lang.NonNull;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import com.example.mymangaapp.mymangaapp.security.service.CustomUserDetailsService;
import com.example.mymangaapp.mymangaapp.security.utils.JwtUtils;

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
            String token = jwtUtils.parseToken(request);

            // Check token ko null và có hợp lệ (chuẩn chữ ký, chưa hết hạn)
            // Nếu là public endpoint thì sẽ ko có header authorization 
            // nên token sẽ null và chạy qua luôn filter này, đền tầng authorization filter
            // vì ko có gì trong context đáng nhẽ bị chặn nhưng nó là public endpoint 
            // nên cứ thể qua tầng authorization filter
            if (token != null && jwtUtils.verifyToken(token)) {

                // Lấy username từ token
                String userId = jwtUtils.extractUserId(token);

                if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                    // Lấy thông tin UserDetails (bao gồm cả Authorities/Permissions) từ db
                    CustomUserDetails customUserDetails = customUserDetailsService.loadUserById(userId);

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
                }
            }

            // Cho phép request đi tiếp tới các filter khác để đi vào controller
            filterChain.doFilter(request, response);

        } catch (Exception e) {
            // Nếu có lỗi Token hết hạn, sai chữ ký... bắn lỗi cho @RestControllerAdvice hứng
            resolver.resolveException(request, response, null, e);
        }

    }

}
