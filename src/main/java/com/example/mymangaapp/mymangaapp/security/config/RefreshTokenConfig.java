package com.example.mymangaapp.mymangaapp.security.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

@Configuration
public class RefreshTokenConfig {

    private static final String PATH = "refresh_token.lua";

    // String là kiểu trả về sau khi đoạn lua script được thực thi
    @Bean
    DefaultRedisScript<String> refreshRotateScript() {
        DefaultRedisScript<String> redisScript = new DefaultRedisScript<>();

        // trỏ tới file lua trong resource
        redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource(PATH)));
        // set kiểu trả về của file lua
        redisScript.setResultType(String.class);

        return redisScript;
    }

}
