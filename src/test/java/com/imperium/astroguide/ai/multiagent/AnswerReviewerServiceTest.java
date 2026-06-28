package com.imperium.astroguide.ai.multiagent;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AnswerReviewerServiceTest {

    private final AnswerReviewerService reviewer =
            new AnswerReviewerService(mock(ChatClient.class), true, false, 40);

    @Test
    void review_passesSubstantiveAnswer() {
        ReviewResult result = reviewer.review(
                "What is a black hole?",
                "A black hole is a region where gravity is so strong that nothing can escape.",
                RouteMode.SIMPLE,
                false);
        assertTrue(result.passed());
        assertEquals("passed", result.reasonCode());
    }

    @Test
    void review_rejectsEmptyAnswer() {
        ReviewResult result = reviewer.review(
                "What is a black hole?",
                "   ",
                RouteMode.SIMPLE,
                false);
        assertFalse(result.passed());
        assertEquals("empty_answer", result.reasonCode());
    }
}
