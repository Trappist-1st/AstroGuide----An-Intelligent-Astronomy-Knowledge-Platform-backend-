package com.imperium.astroguide.ai.runtime;

import com.imperium.astroguide.ai.tool.ToolExecutionRecord;
import com.imperium.astroguide.model.dto.rag.CitationDto;

import java.util.List;
import java.util.Map;

/**
 * Agent Runtime 单次执行结果摘要。
 */
public record AgentRunResult(
        String runId,
        String finalText,
        Integer promptTokens,
        Integer completionTokens,
        List<CitationDto> citations,
        List<ToolExecutionRecord> toolExecutions,
        Map<String, Long> nodeTimingsMs,
        String terminationReason,
        Integer estimatedInputTokens,
        String routeMode,
        String routeReason,
        Boolean reviewPassed,
        String reviewReason) {

    public AgentRunResult(String runId,
            String finalText,
            Integer promptTokens,
            Integer completionTokens,
            List<CitationDto> citations,
            List<ToolExecutionRecord> toolExecutions,
            Map<String, Long> nodeTimingsMs,
            String terminationReason,
            Integer estimatedInputTokens) {
        this(runId, finalText, promptTokens, completionTokens, citations, toolExecutions,
                nodeTimingsMs, terminationReason, estimatedInputTokens, null, null, null, null);
    }
}
