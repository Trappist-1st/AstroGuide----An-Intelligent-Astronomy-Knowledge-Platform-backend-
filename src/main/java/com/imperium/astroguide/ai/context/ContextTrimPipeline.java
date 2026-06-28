package com.imperium.astroguide.ai.context;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 裁剪对话历史：按轮数窗口 + 总字符上限双重约束。
 */
@Component
public class ContextTrimPipeline {

    private final int maxRounds;
    private final int maxHistoryChars;

    public ContextTrimPipeline(
            @Value("${app.ai.context.max-rounds:" + ContextTrimPolicy.DEFAULT_MAX_ROUNDS + "}") int maxRounds,
            @Value("${app.ai.context.max-history-chars:" + ContextTrimPolicy.DEFAULT_MAX_HISTORY_CHARS + "}") int maxHistoryChars) {
        this.maxRounds = maxRounds;
        this.maxHistoryChars = maxHistoryChars;
    }

    public List<Message> trimHistory(List<Message> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        List<Message> windowed = trimByRounds(history, maxRounds);
        return trimByChars(windowed, maxHistoryChars);
    }

    static List<Message> trimByRounds(List<Message> history, int maxRounds) {
        if (maxRounds <= 0 || history.size() <= maxRounds * 2) {
            return new ArrayList<>(history);
        }
        int maxMessages = maxRounds * 2;
        return new ArrayList<>(history.subList(history.size() - maxMessages, history.size()));
    }

    static List<Message> trimByChars(List<Message> history, int maxChars) {
        if (maxChars <= 0 || history.isEmpty()) {
            return history;
        }
        int total = 0;
        List<Message> kept = new ArrayList<>();
        for (int i = history.size() - 1; i >= 0; i--) {
            Message message = history.get(i);
            String text = message != null ? message.getText() : null;
            int len = text != null ? text.length() : 0;
            if (total + len > maxChars && !kept.isEmpty()) {
                break;
            }
            kept.addFirst(cloneMessage(message));
            total += len;
        }
        return kept;
    }

    private static Message cloneMessage(Message message) {
        if (message instanceof UserMessage) {
            return new UserMessage(message.getText());
        }
        if (message instanceof AssistantMessage) {
            return new AssistantMessage(message.getText());
        }
        return message;
    }
}
