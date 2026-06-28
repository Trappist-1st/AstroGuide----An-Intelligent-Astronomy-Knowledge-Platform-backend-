package com.imperium.astroguide.ai.context;

import com.imperium.astroguide.ai.rag.RagRetrievalResult;
import com.imperium.astroguide.model.dto.rag.CitationDto;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ContextAssemblyServiceTest {

    private final ContextAssemblyService assemblyService =
            new ContextAssemblyService(new ContextTrimPipeline(8, 12_000), new TokenBudgetEstimator());

    @Test
    void prepare_injectsRagIntoUserMessage() {
        RagRetrievalResult rag = new RagRetrievalResult(
                "Reference about black holes",
                List.of(new CitationDto("c1", "KB", "excerpt")));
        var prepared = assemblyService.prepare(
                "You are a tutor.",
                List.of(new UserMessage("previous")),
                "What is a black hole?",
                rag);

        assertEquals(3, prepared.messages().size());
        assertTrue(prepared.messages().get(0) instanceof SystemMessage);
        Message user = prepared.messages().getLast();
        assertTrue(user.getText().contains("Reference about black holes"));
        assertTrue(user.getText().contains("What is a black hole?"));
        assertTrue(prepared.estimatedInputTokens() > 0);
    }

    @Test
    void prepare_injectsSummaryIntoSystemMessage() {
        var prepared = assemblyService.prepare(
                "You are a tutor.",
                List.of(new UserMessage("previous")),
                "What is a black hole?",
                RagRetrievalResult.empty(),
                "User asked about stars before.",
                "");

        Message system = prepared.messages().getFirst();
        assertTrue(system.getText().contains("User asked about stars before."));
    }
}
