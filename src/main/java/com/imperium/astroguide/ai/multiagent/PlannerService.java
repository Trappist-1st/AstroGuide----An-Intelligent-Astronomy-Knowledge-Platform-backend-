package com.imperium.astroguide.ai.multiagent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 复杂问题执行计划：为 ReAct Agent 提供结构化步骤，不直接面向用户输出。
 */
@Service
public class PlannerService {

    private static final Logger log = LoggerFactory.getLogger(PlannerService.class);

    private static final String PLANNER_PROMPT = """
            You are an astronomy tutoring planner. Given the user question, produce a concise execution plan \
            (3-5 bullet points) for an assistant that may use tools and reference materials.
            Focus on: key concepts, optional tool usage (Wikipedia/KB), structure of the final answer.
            Output bullet points only, no preamble.
            User question:
            %s
            """;

    private final ChatClient chatClient;
    private final boolean enabled;

    public PlannerService(ChatClient chatClient,
            @Value("${app.ai.planner.enabled:true}") boolean enabled) {
        this.chatClient = chatClient;
        this.enabled = enabled;
    }

    public String plan(String userText) {
        if (!enabled || userText == null || userText.isBlank()) {
            return "";
        }
        try {
            String plan = chatClient.prompt()
                    .user(PLANNER_PROMPT.formatted(userText))
                    .call()
                    .content();
            return plan != null ? plan.trim() : "";
        } catch (Exception e) {
            log.warn("planner failed, fallback to empty plan: {}", e.getMessage());
            return "";
        }
    }
}
