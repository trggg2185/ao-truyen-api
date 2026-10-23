package com.example.mymangaapp.mymangaapp.security.config;

import com.example.mymangaapp.mymangaapp.constant.RoleConstants;
import com.example.mymangaapp.mymangaapp.security.jwt.JwtAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.example.mymangaapp.mymangaapp.security.filter.JwtAuthenticationFilter;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class SecutityConfig {

    JwtAuthenticationFilter jwtAuthenticationFilter;

    JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;

    static String[] POST_PUBLIC_ENDPOINTS = {
            "/auth/social/*", // đăng nhập cũng là đăng ký với google, facebook,...
            "/auth/introspect", // check nhanh token còn valid k
            "/auth/logout", // đăng xuất
            "/auth/refresh" // refresh token
    };

    static String[] GET_PUBLIC_ENDPOINTS = {
            "/transgroups", // lấy tất cả các nhóm dịch đang hoạt động (APPROVED)
            "/transgroups/{id}", // lấy nhóm dịch theo id
            "/mangas", // lấy tất cả các manga
            "/mangas/{id}", // lấy info manga theo id
            "/transgroups/{id}/mangas", // lấy các manga đang dịch của nhóm
            "/mangas/{id}/chapters", // lấy các chapter của 1 manga
            "/chapters/{id}/pages", // lấy các ảnh của 1 chapter
            "/categories", // lấy tất cả các thể loại
            "/categories/{id}", // lấy thể loại theo id
            "/users/{username}" // lấy thông tin user theo username
    };

    static String ADMIN_ENDPOINTS = "/admin/**";

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults()) // active cors để spring biết và dùng bean corsfilter đã đc config
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(request -> request
                        .requestMatchers(HttpMethod.POST, POST_PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.GET, GET_PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(ADMIN_ENDPOINTS).hasRole(RoleConstants.ADMIN)
                        .anyRequest().authenticated())
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint))
                // Đăng ký jwtAuthenticationFilter chạy TRƯỚC
                // UsernamePasswordAuthenticationFilter
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();

    }

}
