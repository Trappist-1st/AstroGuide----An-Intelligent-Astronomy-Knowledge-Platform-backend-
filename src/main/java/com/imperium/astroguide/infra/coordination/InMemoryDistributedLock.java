package com.imperium.astroguide.infra.coordination;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 单机 / Redis 未启用时的进程内锁（同 JVM 多线程有效，不跨实例）。
 */
@Component
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryDistributedLock implements DistributedLock {

    private final ConcurrentHashMap<String, Long> expiresAtMs = new ConcurrentHashMap<>();

    @Override
    public boolean tryLock(String lockKey, long ttlMs) {
        if (lockKey == null || lockKey.isBlank()) {
            return false;
        }
        long now = System.currentTimeMillis();
        long newExpiry = now + ttlMs;
        Long previous = expiresAtMs.get(lockKey);
        if (previous != null && previous > now) {
            return false;
        }
        Long existing = expiresAtMs.putIfAbsent(lockKey, newExpiry);
        if (existing == null) {
            return true;
        }
        if (existing <= now) {
            return expiresAtMs.replace(lockKey, existing, newExpiry);
        }
        return false;
    }

    @Override
    public void unlock(String lockKey) {
        if (lockKey != null) {
            expiresAtMs.remove(lockKey);
        }
    }
}
