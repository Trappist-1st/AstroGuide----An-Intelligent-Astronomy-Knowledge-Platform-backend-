package com.imperium.astroguide.infra.ratelimit;

public interface RateLimitBackend {

    boolean allow(String key, long windowMs, int maxRequests);
}
