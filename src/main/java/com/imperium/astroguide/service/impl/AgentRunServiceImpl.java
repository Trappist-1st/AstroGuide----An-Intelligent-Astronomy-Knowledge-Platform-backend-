package com.imperium.astroguide.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imperium.astroguide.ai.runtime.AgentRunResult;
import com.imperium.astroguide.ai.tool.ToolExecutionRecord;
import com.imperium.astroguide.mapper.AgentRunMapper;
import com.imperium.astroguide.model.entity.AgentRun;
import com.imperium.astroguide.service.AgentRunService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class AgentRunServiceImpl implements AgentRunService {

    private final AgentRunMapper agentRunMapper;
    private final ObjectMapper objectMapper;

    public AgentRunServiceImpl(AgentRunMapper agentRunMapper, ObjectMapper objectMapper) {
        this.agentRunMapper = agentRunMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public void markRunning(String runId,
            String requestId,
            String conversationId,
            String messageId,
            String model) {
        AgentRun run = new AgentRun();
        run.setId(runId);
        run.setRequestId(requestId);
        run.setConversationId(conversationId);
        run.setMessageId(messageId);
        run.setStatus("running");
        run.setModel(model);
        run.setCreatedAt(LocalDateTime.now());
        agentRunMapper.insert(run);
    }

    @Override
    public void markFinished(AgentRunResult result, int latencyMs, String status, String errorMessage) {
        AgentRun run = agentRunMapper.selectById(result.runId());
        if (run == null) {
            return;
        }
        run.setStatus(status);
        run.setPromptTokens(result.promptTokens());
        run.setCompletionTokens(result.completionTokens());
        run.setLatencyMs(latencyMs);
        run.setTerminationReason(result.terminationReason());
        run.setErrorMessage(errorMessage);
        run.setToolCallsJson(toJson(result.toolExecutions()));
        run.setNodeTimingsJson(toJson(result.nodeTimingsMs()));
        run.setFinishedAt(LocalDateTime.now());
        agentRunMapper.updateById(run);
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "[]";
        }
    }
}
