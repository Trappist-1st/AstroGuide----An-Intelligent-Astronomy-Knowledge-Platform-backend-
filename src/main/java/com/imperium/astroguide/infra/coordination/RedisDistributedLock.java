package com.imperium.astroguide.infra.coordination;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "true")
public class RedisDistributedLock implements DistributedLock {

    private static final String PREFIX = "astroguide:lock:";

    private final StringRedisTemplate redisTemplate;
    private final String lockOwner = UUID.randomUUID().toString();

    public RedisDistributedLock(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public boolean tryLock(String lockKey, long ttlMs) {
        if (lockKey == null || lockKey.isBlank()) {
            return false;
        }
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(
                PREFIX + lockKey,
                lockOwner,
                Duration.ofMillis(Math.max(ttlMs, 1L)));
        return Boolean.TRUE.equals(acquired);
    }

    @Override
    public void unlock(String lockKey) {
        if (lockKey == null || lockKey.isBlank()) {
            return;
        }
        String redisKey = PREFIX + lockKey;
        String value = redisTemplate.opsForValue().get(redisKey);
        if (lockOwner.equals(value)) {
            redisTemplate.delete(redisKey);
        }
    }
}
