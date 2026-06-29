package com.imperium.astroguide.ai.orchestrator;

/**
 * 线程安全的流式文本缓冲（delta 与 cancel 可能并发访问）。
 */
public final class StreamContentBuffer {

    private final StringBuilder buffer = new StringBuilder();

    public synchronized void append(String text) {
        if (text != null && !text.isBlank()) {
            buffer.append(text);
        }
    }

    public synchronized String snapshot() {
        return buffer.toString();
    }
}
