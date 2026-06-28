package com.imperium.astroguide.ai.runtime;

import com.imperium.astroguide.ai.tool.ToolExecutionRecord;
import com.imperium.astroguide.model.dto.rag.CitationDto;
import org.springframework.ai.chat.messages.Message;

import java.util.List;

/**
 * Agent Runtime 单次执行请求。
 */
public record AgentRunRequest(
        String runId,
        String requestId,
        String conversationId,
        String messageId,
        String systemPrompt,
        String userText,
        List<Message> historyMessages,
        Integer maxCompletionTokens,
        List<CitationDto> ragCitations,
        String conversationSummary) {

    public AgentRunRequest(String runId,
            String requestId,
            String conversationId,
            String messageId,
            String systemPrompt,
            String userText,
            List<Message> historyMessages,
            Integer maxCompletionTokens,
            List<CitationDto> ragCitations) {
        this(runId, requestId, conversationId, messageId, systemPrompt, userText,
                historyMessages, maxCompletionTokens, ragCitations, "");
    }
}
