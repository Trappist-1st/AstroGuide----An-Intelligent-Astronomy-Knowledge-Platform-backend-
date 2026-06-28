package com.imperium.astroguide.service;

import com.imperium.astroguide.ai.runtime.AgentRunResult;

public interface AgentRunService {

    void markRunning(String runId,
            String requestId,
            String conversationId,
            String messageId,
            String model);

    void markFinished(AgentRunResult result, int latencyMs, String status, String errorMessage);
}
