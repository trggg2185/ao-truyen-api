package com.example.mymangaapp.mymangaapp.aop;

import com.example.mymangaapp.mymangaapp.annotation.RateLimit;
import com.example.mymangaapp.mymangaapp.enums.LimitType;
import com.example.mymangaapp.mymangaapp.exception.AppException;
import com.example.mymangaapp.mymangaapp.exception.ResponseCode;
import com.example.mymangaapp.mymangaapp.security.utils.SecurityUtils;
import com.example.mymangaapp.mymangaapp.utils.HttpUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collections;

@Aspect
@Component
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@Slf4j
public class RateLimitAspect {

    StringRedisTemplate redisTemplate;
    DefaultRedisScript<Long> redisScript;

    static String REDIS_KEY_PREFIX = "mymangaapp:rate-limit:";

    // Chặn tất cả các method có gắn annotation rate limit
    @Around("@annotation(rateLimitAnnotation)")
    public Object rateLimiting(ProceedingJoinPoint joinPoint, RateLimit rateLimitAnnotation) throws Throwable {

        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();

        if (requestAttributes == null) {
            log.warn("Rate limit được gọi ngoài Http request context tại {} nên tự động cho qua!",
                    joinPoint.getSignature().toShortString());

            return joinPoint.proceed();
        }

        HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
        String redisKey;

        // http method
        String httpMethod = request.getMethod();
        // path
        String path = request.getRequestURI();

        String actionScope = httpMethod + ":" + path;

        // Chỉ lấy userId nếu yêu cầu LimitType là USER_ID
        String userId = rateLimitAnnotation.limitType() == LimitType.USER_ID
                ? SecurityUtils.findCurrentUserId().orElse(null)
                : null;

        // Nếu có userId thì limit theo id
        if (userId != null) {
            redisKey = REDIS_KEY_PREFIX + "user:" + userId + ":" + actionScope;
        } else { // ko thì limit theo ip
            String clientIp = HttpUtils.getClientIp(request);
            redisKey = REDIS_KEY_PREFIX + "ip:" + clientIp + ":" + actionScope;
        }

        try {
            // chạy lua script dưới redis
            Long result = redisTemplate.execute(
                    redisScript,
                    Collections.singletonList(redisKey), // KEY[1]
                    String.valueOf(rateLimitAnnotation.resetTimeInSeconds()), // ARGV[1]
                    String.valueOf(rateLimitAnnotation.capacity()) // ARGV[2]
            );

            // check an toàn
            if (Long.valueOf(0L).equals(result)) {
                throw new AppException(ResponseCode.RATE_LIMIT_EXCEEDED);
            }
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("Rate limit check failed for {} {}", httpMethod, path, exception);
        }

        // cho phép request đi qua
        return joinPoint.proceed();
    }

}
