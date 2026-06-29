package com.imperium.astroguide.infra.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

@Component
@ConditionalOnProperty(name = "app.redis.enabled", havingValue = "false", matchIfMissing = true)
public class InMemoryRateLimitBackend implements RateLimitBackend {

    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();

    @Override
    public boolean allow(String key, long windowMs, int maxRequests) {
        long now = System.currentTimeMillis();
        Window w = windows.compute(key, (k, old) -> {
            if (old == null || now - old.startMs > windowMs) {
                return new Window(now, 1);
            }
            return new Window(old.startMs, old.count + 1);
        });
        return w.count <= maxRequests;
    }

    private record Window(long startMs, int count) {}
}
