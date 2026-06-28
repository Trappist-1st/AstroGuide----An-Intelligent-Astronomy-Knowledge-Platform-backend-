package com.imperium.astroguide.ai.context;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextTrimPipelineTest {

    private final ContextTrimPipeline pipeline = new ContextTrimPipeline(2, 500);

    @Test
    void trimByRounds_keepsLatestRounds() {
        List<Message> history = IntStream.range(0, 6)
                .mapToObj(i -> (Message) new UserMessage("u" + i))
                .toList();
        history = new java.util.ArrayList<>(history);
        history.add(new AssistantMessage("a0"));
        history.add(new UserMessage("u6"));
        history.add(new AssistantMessage("a1"));

        List<Message> trimmed = ContextTrimPipeline.trimByRounds(history, 2);
        assertEquals(4, trimmed.size());
        assertTrue(trimmed.get(0).getText().contains("u5"));
    }

    @Test
    void trimHistory_appliesRoundAndCharLimits() {
        List<Message> history = List.of(
                new UserMessage("x".repeat(400)),
                new AssistantMessage("y".repeat(400)),
                new UserMessage("latest question"));
        List<Message> trimmed = pipeline.trimHistory(history);
        assertTrue(trimmed.size() <= 2);
        assertEquals("latest question", trimmed.getLast().getText());
    }
}
