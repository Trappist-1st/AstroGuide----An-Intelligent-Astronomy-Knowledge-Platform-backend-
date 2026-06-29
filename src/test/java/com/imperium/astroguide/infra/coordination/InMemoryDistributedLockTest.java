package com.imperium.astroguide.infra.coordination;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryDistributedLockTest {

    private final InMemoryDistributedLock lock = new InMemoryDistributedLock();

    @Test
    void tryLock_exclusiveUntilUnlock() {
        String key = "summary:conv-1";
        assertTrue(lock.tryLock(key, 5000));
        assertFalse(lock.tryLock(key, 5000));
        lock.unlock(key);
        assertTrue(lock.tryLock(key, 5000));
        lock.unlock(key);
    }
}
