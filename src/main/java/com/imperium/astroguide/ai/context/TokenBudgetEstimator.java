package com.imperium.astroguide.ai.context;

import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Token 粗估：Phase 2 使用字符/heuristic 估算，避免引入额外 tokenizer 依赖。
 */
@Component
public class TokenBudgetEstimator {

    /** 英文约 4 chars/token，中文约 1.5 chars/token，取保守均值 */
    private static final double CHARS_PER_TOKEN = 3.0;

    public int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return (int) Math.ceil(text.length() / CHARS_PER_TOKEN);
    }

    public int estimateTokens(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (Message message : messages) {
            if (message == null || message.getText() == null) {
                continue;
            }
            total += estimateTokens(message.getText());
        }
        return total;
    }
}
