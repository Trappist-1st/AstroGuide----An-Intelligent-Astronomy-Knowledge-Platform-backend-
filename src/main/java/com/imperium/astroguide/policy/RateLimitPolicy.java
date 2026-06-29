package com.imperium.astroguide.policy;

import com.imperium.astroguide.infra.ratelimit.RateLimitBackend;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 限流策略：按 clientId + IP；后端可切换为 Redis 分布式计数。
 */
@Component
public class RateLimitPolicy {

    public static final long WINDOW_MS = 10 * 60 * 1000L;
    public static final int MAX_REQUESTS_PER_WINDOW = 20;

    private final RateLimitBackend rateLimitBackend;
    private final long windowMs;
    private final int maxRequests;

    public RateLimitPolicy(RateLimitBackend rateLimitBackend,
            @Value("${app.rate-limit.window-ms:" + WINDOW_MS + "}") long windowMs,
            @Value("${app.rate-limit.max-requests:" + MAX_REQUESTS_PER_WINDOW + "}") int maxRequests) {
        this.rateLimitBackend = rateLimitBackend;
        this.windowMs = windowMs;
        this.maxRequests = maxRequests;
    }

    public boolean allow(String clientId, String clientIp) {
        String key = (clientId != null ? clientId : "") + "|" + (clientIp != null ? clientIp : "");
        return rateLimitBackend.allow(key, windowMs, maxRequests);
    }
}
