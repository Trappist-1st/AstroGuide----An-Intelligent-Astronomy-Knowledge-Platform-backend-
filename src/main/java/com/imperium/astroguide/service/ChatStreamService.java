package com.imperium.astroguide.service;

import org.springframework.ai.chat.client.ChatClientResponse;
import reactor.core.publisher.Flux;

import java.util.Map;

/**
 * AI 流式回答服务：调用 LLM 流式接口，按 delta 返回文本。
 */
public interface ChatStreamService {

    /**
     * 流式生成回答，返回增量文本序列（使用默认 maxTokens）。
     */
    Flux<String> streamContent(String systemPrompt, String userContent, String model);

    /**
     * 流式生成回答，可指定最大回复 token 数（成本控制）。
     * @param systemPrompt 系统提示（难度、语言等约束）
     * @param userContent  用户问题内容
     * @param model        可选模型名（用于 meta 事件）
     * @param maxCompletionTokens 最大 completion tokens，null 表示使用默认
     * @return 增量文本 Flux，每项为一小段内容
     */
    Flux<String> streamContent(String systemPrompt, String userContent, String model, Integer maxCompletionTokens);

    /**
     * 基于 Spring AI ChatClient Advisors 的流式响应。
     * 返回 {@link ChatClientResponse} 以便读取 advisor context（如 RAG 检索到的 Documents）并生成 citations。
     */
    Flux<ChatClientResponse> streamChatClientResponses(
            String conversationId,
            String systemPrompt,
            String userText,
            String model,
            Integer maxCompletionTokens,
            boolean ragEnabled,
            boolean wikipediaOnDemandEnabled,
            boolean toolCallingEnabled,
            Map<String, Object> advisorParams);
}
