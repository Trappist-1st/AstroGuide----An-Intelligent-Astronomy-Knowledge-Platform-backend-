package com.imperium.astroguide.ai.orchestrator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StreamContentBufferTest {

    @Test
    void appendAndSnapshot_threadSafe() throws InterruptedException {
        StreamContentBuffer buffer = new StreamContentBuffer();
        Thread t1 = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                buffer.append("a");
            }
        });
        Thread t2 = new Thread(() -> {
            for (int i = 0; i < 100; i++) {
                buffer.append("b");
            }
        });
        t1.start();
        t2.start();
        t1.join();
        t2.join();
        assertEquals(200, buffer.snapshot().length());
    }
}
