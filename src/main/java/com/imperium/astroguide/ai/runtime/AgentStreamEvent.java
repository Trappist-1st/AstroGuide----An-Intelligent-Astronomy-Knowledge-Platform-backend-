package com.imperium.astroguide.ai.runtime;

import com.imperium.astroguide.ai.tool.ToolExecutionRecord;

import java.util.Map;

/**
 * Agent Runtime 流式事件：供 Orchestrator 映射为 SSE。
 */
public sealed interface AgentStreamEvent permits
        AgentStreamEvent.TextDelta,
        AgentStreamEvent.NodeStarted,
        AgentStreamEvent.NodeFinished,
        AgentStreamEvent.ToolStarted,
        AgentStreamEvent.ToolFinished,
        AgentStreamEvent.RouteSelected,
        AgentStreamEvent.ReviewCompleted,
        AgentStreamEvent.RunFinished {

    record TextDelta(String text) implements AgentStreamEvent {
    }

    record NodeStarted(String nodeId) implements AgentStreamEvent {
    }

    record NodeFinished(String nodeId, long latencyMs) implements AgentStreamEvent {
    }

    record ToolStarted(String toolName, String arguments) implements AgentStreamEvent {
    }

    record ToolFinished(ToolExecutionRecord record) implements AgentStreamEvent {
    }

    record RouteSelected(String mode, String reasonCode, double confidence) implements AgentStreamEvent {
    }

    record ReviewCompleted(boolean passed, String reasonCode, String finalTextOverride) implements AgentStreamEvent {
    }

    record RunFinished(AgentRunResult result) implements AgentStreamEvent {
    }
}
