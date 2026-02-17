package com.imperium.astroguide.controller;

import com.imperium.astroguide.ai.orchestrator.ChatStreamOrchestrator;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

/**
 * AI 流式回答接口，按 TDD 5.3 实现 SSE 流式输出。
 * 含：上下文裁剪、最大输出限制、限流、流式落库、取消标记、Concept Markers 协议。
 */
@RestController
@RequestMapping("/api/v0/conversations")
public class AIChatController {

    private static final String HEADER_CLIENT_ID = "X-Client-Id";

    private final ChatStreamOrchestrator chatStreamOrchestrator;

    public AIChatController(ChatStreamOrchestrator chatStreamOrchestrator) {
        this.chatStreamOrchestrator = chatStreamOrchestrator;
    }

    /**
     * 流式获取本次 assistant 回复。
     * TDD: GET /conversations/{conversationId}/messages/{messageId}/stream
     * 必需请求头：X-Client-Id
     * 前端关闭 EventSource 即视为取消；后端将 assistant 消息标为 cancelled。
     */
    @GetMapping(value = "/{conversationId}/messages/{messageId}/stream",
                produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> stream(
            @PathVariable String conversationId,
            @PathVariable String messageId,
            @RequestHeader(value = HEADER_CLIENT_ID, required = false) String clientId,
            HttpServletRequest request) {
        return chatStreamOrchestrator.stream(conversationId, messageId, clientId, request);
    }
}
