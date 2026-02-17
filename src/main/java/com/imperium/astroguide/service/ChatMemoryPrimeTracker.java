package com.imperium.astroguide.service;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 跟踪每个会话已 prime 到 ChatMemory 的 DB 游标，支持增量 prime。
 *
 * 说明：DB 仍是权威来源；该游标仅用于减少每次请求都 clear+rebuild 的开销。
 * 若发生重启/漂移/乱序请求，可回退为 rebuild。
 */
@Component
public class ChatMemoryPrimeTracker {

    public record PrimeCursor(LocalDateTime createdAt, String messageId) {
    }

    private final ConcurrentHashMap<String, PrimeCursor> cursorByConversationId = new ConcurrentHashMap<>();

    public PrimeCursor getCursor(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return null;
        }
        return cursorByConversationId.get(conversationId);
    }

    public void clearCursor(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        cursorByConversationId.remove(conversationId);
    }

    public void updateCursor(String conversationId, PrimeCursor newCursor) {
        if (conversationId == null || conversationId.isBlank() || newCursor == null || newCursor.createdAt() == null) {
            return;
        }

        cursorByConversationId.compute(conversationId, (k, old) -> {
            if (old == null) {
                return newCursor;
            }
            int cmp = old.createdAt().compareTo(newCursor.createdAt());
            if (cmp < 0) {
                return newCursor;
            }
            if (cmp == 0) {
                String oldId = old.messageId();
                String newId = newCursor.messageId();
                if (oldId == null) {
                    return newCursor;
                }
                if (newId != null && oldId.compareTo(newId) < 0) {
                    return newCursor;
                }
            }
            return old;
        });
    }

    public void updateCursor(String conversationId, LocalDateTime createdAt, String messageId) {
        updateCursor(conversationId, new PrimeCursor(createdAt, messageId));
    }

    public boolean isBeforeOrEqual(PrimeCursor a, PrimeCursor b) {
        if (a == null || b == null || a.createdAt() == null || b.createdAt() == null) {
            return false;
        }
        int cmp = a.createdAt().compareTo(b.createdAt());
        if (cmp < 0) {
            return true;
        }
        if (cmp > 0) {
            return false;
        }
        if (Objects.equals(a.messageId(), b.messageId())) {
            return true;
        }
        if (a.messageId() == null || b.messageId() == null) {
            return false;
        }
        return a.messageId().compareTo(b.messageId()) <= 0;
    }
}
