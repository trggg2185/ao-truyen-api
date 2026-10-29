package com.example.mymangaapp.mymangaapp.utils;

import jakarta.servlet.http.HttpServletRequest;

public final class HttpUtils {

    private HttpUtils() {
        /* This utility class should not be instantiated */
    }

    // Lấy client ip thật, do lên production thì thường app chạy sau nginx, load balancer hoặc cloudflare
    // nên nếu dùng request.getRemoteAddr() thì chỉ lấy đc ip của proxy và tất cả user đều trả về
    // ip proxy đó thế là limit tất cả user luôn
    public static String getClientIp(HttpServletRequest request) {

        String[] headers = {
                "X-Forwarded-For",
                "X-Real-IP",
                "Proxy-Client-IP",
                "WL-Proxy-Client-IP",
        };

        for (String header : headers) {
            String ip = request.getHeader(header);

            if (ip != null && !ip.isBlank() && !"unknown".equalsIgnoreCase(ip)) {
                // trả về split vì X-Forward-For có thể chứa nhiều ip
                return ip.split(",")[0].trim();
            }
        }

        return request.getRemoteAddr();
    }

}
