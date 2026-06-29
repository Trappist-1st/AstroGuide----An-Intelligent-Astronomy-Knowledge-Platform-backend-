package com.imperium.astroguide.infra.ratelimit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryRateLimitBackendTest {

    private final InMemoryRateLimitBackend backend = new InMemoryRateLimitBackend();

    @Test
    void allow_respectsMaxWithinWindow() {
        String key = "client|ip";
        long windowMs = 60_000;
        int max = 3;
        assertTrue(backend.allow(key, windowMs, max));
        assertTrue(backend.allow(key, windowMs, max));
        assertTrue(backend.allow(key, windowMs, max));
        assertFalse(backend.allow(key, windowMs, max));
    }
}
