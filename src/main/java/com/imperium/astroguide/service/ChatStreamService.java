package com.imperium.astroguide.service;

import org.springframework.ai.chat.client.ChatClientResponse;
import reactor.core.publisher.Flux;

/**
 * AI 流式回答服务：调用 LLM 流式接口，按 delta 返回 ChatClientResponse。
 * <p>
 * RAG、Tool Calling、ChatMemory 等能力均由实现类内部通过 Spring AI Advisor / Tool 机制管理，
 * 调用方无需关心具体开关。
 */
public interface ChatStreamService {

    /**
     * 基于 Spring AI ChatClient 的流式响应。
     * <p>
     * 内部自动挂载 MessageChatMemoryAdvisor（对话记忆）、QuestionAnswerAdvisor（RAG）以及
     * Tool Calling（WikipediaTool / KnowledgeBaseTool / ConceptCardTool）。
     *
     * @param conversationId      会话 ID（用于 ChatMemory advisor）
     * @param systemPrompt        系统提示词
     * @param userText            用户输入文本
     * @param maxCompletionTokens 最大 completion tokens，null 表示使用默认
     * @return ChatClientResponse 流，可从中提取 delta 文本和 advisor context
     */
    Flux<ChatClientResponse> streamChatClientResponses(
            String conversationId,
            String systemPrompt,
            String userText,
            Integer maxCompletionTokens);
}
