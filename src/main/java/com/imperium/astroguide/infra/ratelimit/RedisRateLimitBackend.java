package com.imperium.astroguide.infra.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 固定窗口计数限流（Lua 保证 INCR + EXPIRE 原子性，多实例共享）。
 */
@Component
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")
public class RedisRateLimitBackend implements RateLimitBackend {

    private static final String PREFIX = "astroguide:ratelimit:";

    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            if current > tonumber(ARGV[2]) then
              return 0
            end
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimitBackend(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean allow(String key, long windowMs, int maxRequests) {
        Long allowed = redisTemplate.execute(
                SCRIPT,
                List.of(PREFIX + key),
                String.valueOf(windowMs),
                String.valueOf(maxRequests));
        return allowed != null && allowed == 1L;
    }
}
