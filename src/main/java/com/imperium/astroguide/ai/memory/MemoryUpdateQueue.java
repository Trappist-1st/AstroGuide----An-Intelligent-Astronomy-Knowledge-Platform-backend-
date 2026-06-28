package com.imperium.astroguide.ai.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 记忆更新防抖队列：同一会话在 debounce 窗口内合并为一次摘要任务。
 */
@Component
public class MemoryUpdateQueue {

    private static final Logger log = LoggerFactory.getLogger(MemoryUpdateQueue.class);

    private final SummaryMemoryWorker summaryMemoryWorker;
    private final long debounceMs;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "memory-update-queue");
        t.setDaemon(true);
        return t;
    });
    private final ConcurrentHashMap<String, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();

    public MemoryUpdateQueue(SummaryMemoryWorker summaryMemoryWorker,
            @Value("${app.ai.memory.summary-debounce-ms:5000}") long debounceMs) {
        this.summaryMemoryWorker = summaryMemoryWorker;
        this.debounceMs = debounceMs;
    }

    public void enqueue(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        ScheduledFuture<?> previous = pending.remove(conversationId);
        if (previous != null) {
            previous.cancel(false);
        }
        ScheduledFuture<?> future = scheduler.schedule(() -> {
            pending.remove(conversationId);
            try {
                summaryMemoryWorker.summarizeConversation(conversationId);
            } catch (Exception e) {
                log.warn("summary memory worker failed conversationId={} error={}", conversationId, e.getMessage());
            }
        }, debounceMs, TimeUnit.MILLISECONDS);
        pending.put(conversationId, future);
        log.debug("memory update queued conversationId={} debounceMs={}", conversationId, debounceMs);
    }
}
