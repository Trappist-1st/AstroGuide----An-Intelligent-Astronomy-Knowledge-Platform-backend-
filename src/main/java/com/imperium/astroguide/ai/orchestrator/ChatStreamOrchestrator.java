package com.imperium.astroguide.ai.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imperium.astroguide.model.dto.rag.CitationDto;
import com.imperium.astroguide.model.entity.Conversation;
import com.imperium.astroguide.model.entity.Message;
import com.imperium.astroguide.policy.OutputLimitPolicy;
import com.imperium.astroguide.policy.RateLimitPolicy;
import com.imperium.astroguide.service.ChatStreamService;
import com.imperium.astroguide.service.ConversationService;
import com.imperium.astroguide.service.MessageService;
import com.imperium.astroguide.service.UsageService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

/**
 * SSE 流式对话编排器。
 * <p>
 * 职责：请求验证 → 限流 → 加载历史记忆 → 调用 ChatStreamService 流式生成 → 落库 → SSE 事件组装。
 * <p>
 * RAG、Tool Calling、ChatMemory 等能力由 {@link ChatStreamService} 内部管理，
 * 本类不关心具体开关和实现细节。
 */
@Service
public class ChatStreamOrchestrator {

    private final ConversationService conversationService;
    private final MessageService messageService;
    private final ChatStreamService chatStreamService;
    private final UsageService usageService;
    private final RateLimitPolicy rateLimitPolicy;
    private final ObjectMapper objectMapper;
    private final ChatMemory chatMemory;

    @Value("${spring.ai.openai.chat.options.model:deepseek-chat}")
    private String defaultModel;

    public ChatStreamOrchestrator(ConversationService conversationService,
            MessageService messageService,
            ChatStreamService chatStreamService,
            UsageService usageService,
            RateLimitPolicy rateLimitPolicy,
            ObjectMapper objectMapper,
            ChatMemory chatMemory) {
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.chatStreamService = chatStreamService;
        this.usageService = usageService;
        this.rateLimitPolicy = rateLimitPolicy;
        this.objectMapper = objectMapper;
        this.chatMemory = chatMemory;
    }

    // ==================== 公开入口 ====================

    public Flux<ServerSentEvent<String>> stream(String conversationId,
            String messageId,
            String clientId,
            HttpServletRequest request) {

        // ---------- 1. 验证 ----------
        ServerSentEvent<String> error = validate(conversationId, messageId, clientId, request);
        if (error != null)
            return Flux.just(error);

        Conversation conversation = conversationService.getById(conversationId);
        if (conversation == null)
            return Flux.just(sseError("not_found", "Conversation not found", null));
        if (!Objects.equals(conversation.getClientId(), clientId))
            return Flux.just(sseError("forbidden", "clientId does not match conversation", null));

        Message userMessage = messageService.getById(messageId);
        if (userMessage == null || !conversationId.equals(userMessage.getConversationId()))
            return Flux.just(sseError("not_found", "Message not found", null));
        if (!"user".equals(userMessage.getRole()))
            return Flux.just(sseError("invalid_argument", "Message is not a user message", null));

        // ---------- 2. 准备参数 ----------
        String difficulty = userMessage.getDifficulty() != null ? userMessage.getDifficulty() : "intermediate";
        String language = userMessage.getLanguage() != null ? userMessage.getLanguage() : "en";
        String userQuestion = userMessage.getContent() != null ? userMessage.getContent() : "";
        int maxTokens = OutputLimitPolicy.getMaxCompletionTokens(difficulty);
        String requestId = "req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        String systemPrompt = buildSystemPrompt(difficulty, language);
        String assistantMessageId = messageId + "_a";

        // 首次请求时，将 DB 中历史消息加载到 ChatMemory（一次性，非增量）
        ensureChatMemoryLoaded(conversationId, userMessage);

        // 标记 assistant 消息为 streaming
        Message assistantMsg = messageService.getById(assistantMessageId);
        if (assistantMsg != null) {
            assistantMsg.setStatus("streaming");
            messageService.updateById(assistantMsg);
        }

        // ---------- 3. Meta 事件 ----------
        Flux<ServerSentEvent<String>> metaFlux = Flux.just(
                ServerSentEvent.<String>builder(toJson(Map.of(
                        "requestId", requestId,
                        "model", defaultModel,
                        "difficulty", difficulty,
                        "language", language))).event("meta").build());

        // ---------- 4. 流式 LLM 调用 + SSE ----------
        long startMs = System.currentTimeMillis();
        StringBuilder contentAccumulator = new StringBuilder();
        AtomicReference<Integer> promptTokensRef = new AtomicReference<>();
        AtomicReference<Integer> completionTokensRef = new AtomicReference<>();
        AtomicReference<List<Document>> ragDocsRef = new AtomicReference<>();

        return metaFlux.concatWith(Flux.defer(() -> {
            Flux<ChatClientResponse> responseFlux = chatStreamService
                    .streamChatClientResponses(conversationId, systemPrompt, userQuestion, maxTokens)
                    .doOnNext(r -> {
                        captureUsage(r, promptTokensRef, completionTokensRef);
                        captureRagDocuments(r, ragDocsRef);
                    });

            // delta 事件：按块缓冲，减少「一词一 event」，每约 10 个 token 或合并后发一条
            Flux<ServerSentEvent<String>> deltaFlux = responseFlux
                    .map(ChatStreamOrchestrator::extractDeltaText)
                    .filter(s -> s != null && !s.isBlank())
                    .buffer(10)
                    .map(chunks -> String.join("", chunks))
                    .filter(s -> !s.isEmpty())
                    .doOnNext(contentAccumulator::append)
                    .map(chunk -> ServerSentEvent.<String>builder(
                            toJson(Map.of("text", chunk))).event("delta").build());

            // done 事件
            Flux<ServerSentEvent<String>> doneFlux = Flux.defer(() -> {
                int latencyMs = (int) (System.currentTimeMillis() - startMs);
                Integer promptTok = promptTokensRef.get();
                Integer completionTok = completionTokensRef.get();

                Map<String, Object> donePayload = new HashMap<>(Map.of("status", "done"));
                if (promptTok != null || completionTok != null) {
                    Map<String, Object> usage = new HashMap<>();
                    if (promptTok != null)
                        usage.put("promptTokens", promptTok);
                    if (completionTok != null)
                        usage.put("completionTokens", completionTok);
                    donePayload.put("usage", usage);
                }

                List<CitationDto> citations = buildCitations(ragDocsRef.get());
                if (!citations.isEmpty()) {
                    donePayload.put("citations", citations.stream().map(c -> Map.of(
                            "chunkId", c.getChunkId() != null ? c.getChunkId() : "",
                            "source", c.getSource() != null ? c.getSource() : "",
                            "excerpt", c.getExcerpt() != null ? c.getExcerpt() : "")).toList());
                }

                finishAssistantMessage(assistantMessageId, contentAccumulator.toString(),
                        "done", null, null, promptTok, completionTok, null);
                usageService.record(assistantMessageId, defaultModel, latencyMs,
                        promptTok, completionTok, null);

                return Flux.just(ServerSentEvent.<String>builder(toJson(donePayload)).event("done").build());
            }).subscribeOn(Schedulers.boundedElastic());

            return deltaFlux
                    .concatWith(doneFlux)
                    .onErrorResume(e -> Flux.defer(() -> {
                        finishAssistantMessage(assistantMessageId, contentAccumulator.toString(),
                                "error", "provider_error",
                                e.getMessage() != null ? e.getMessage() : "LLM stream failed",
                                null, null, null);
                        return Flux.just(sseError("provider_error",
                                e.getMessage() != null ? e.getMessage() : "LLM stream failed", requestId));
                    }).subscribeOn(Schedulers.boundedElastic()))
                    .doOnCancel(() -> Schedulers.boundedElastic()
                            .schedule(() -> finishAssistantMessage(assistantMessageId, contentAccumulator.toString(),
                                    "cancelled", null, null, null, null, null)));
        }));
    }

    // ==================== 私有：验证 ====================

    private ServerSentEvent<String> validate(String conversationId, String messageId,
            String clientId, HttpServletRequest request) {
        if (clientId == null || clientId.isBlank())
            return sseError("invalid_argument", "X-Client-Id is required", null);

        String clientIp = request != null ? request.getRemoteAddr() : null;
        if (!rateLimitPolicy.allow(clientId, clientIp))
            return sseError("rate_limited", "Too many requests", null);

        return null; // 验证通过
    }

    // ==================== 私有：ChatMemory 加载 ====================

    /**
     * 若 ChatMemory 中还没有该会话的历史消息，则从 DB 一次性加载最近的历史记录。
     * <p>
     * 取代了原来的 primeChatMemoryFromDb / rebuildChatMemoryFromDb /
     * incrementalPrimeChatMemoryFromDb
     * 以及 ChatMemoryPrimeTracker 的增量游标机制。
     */
    private void ensureChatMemoryLoaded(String conversationId, Message currentUserMessage) {
        if (conversationId == null || conversationId.isBlank())
            return;
        if (currentUserMessage.getCreatedAt() == null)
            return;

        var existing = chatMemory.get(conversationId);
        if (!existing.isEmpty())
            return; // 已加载过

        List<Message> history = messageService.lambdaQuery()
                .eq(Message::getConversationId, conversationId)
                .lt(Message::getCreatedAt, currentUserMessage.getCreatedAt())
                .orderByAsc(Message::getCreatedAt)
                .last("LIMIT 16")
                .list();

        List<org.springframework.ai.chat.messages.Message> memoryMsgs = new ArrayList<>();
        for (Message m : history) {
            if (m == null || m.getContent() == null || m.getContent().isBlank())
                continue;
            if ("assistant".equals(m.getRole())) {
                if ("queued".equalsIgnoreCase(m.getStatus()))
                    continue;
                memoryMsgs.add(new AssistantMessage(m.getContent()));
            } else {
                memoryMsgs.add(new UserMessage(m.getContent()));
            }
        }

        if (!memoryMsgs.isEmpty()) {
            chatMemory.add(conversationId, memoryMsgs);
        }
    }

    // ==================== 私有：响应提取 ====================

    /** 从流式 ChatResponse 中获取真实 Usage（由 LLM 提供商返回） */
    private static void captureUsage(ChatClientResponse response,
            AtomicReference<Integer> promptRef,
            AtomicReference<Integer> completionRef) {
        if (response == null || response.chatResponse() == null)
            return;
        ChatResponse cr = response.chatResponse();
        if (cr.getMetadata().getUsage() == null)
            return;

        var usage = cr.getMetadata().getUsage();
        if (usage.getPromptTokens() != null && usage.getPromptTokens() > 0)
            promptRef.set(usage.getPromptTokens());
        if (usage.getCompletionTokens() != null && usage.getCompletionTokens() > 0)
            completionRef.set(usage.getCompletionTokens());
    }

    /** 捕获 RAG advisor 检索到的文档（用于生成 citations） */
    private static void captureRagDocuments(ChatClientResponse response,
            AtomicReference<List<Document>> ragDocsRef) {
        if (ragDocsRef.get() != null)
            return;
        if (response == null)
            return;
        Object ragObj = response.context().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);
        if (ragObj instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Document) {
            @SuppressWarnings("unchecked")
            List<Document> docs = (List<Document>) list;
            ragDocsRef.set(docs);
        }
    }

    private static String extractDeltaText(ChatClientResponse response) {
        if (response == null || response.chatResponse() == null) {
            return null;
        }
        return response.chatResponse().getResult().getOutput().getText();
    }

    // ==================== 私有：Citations ====================

    private static List<CitationDto> buildCitations(List<Document> docs) {
        if (docs == null || docs.isEmpty())
            return List.of();
        List<CitationDto> citations = new ArrayList<>();
        for (int i = 0; i < docs.size(); i++) {
            Document d = docs.get(i);
            if (d == null || d.getText() == null || d.getText().isBlank())
                continue;
            Map<String, Object> meta = d.getMetadata();
            String source = meta.get("source") != null ? meta.get("source").toString()
                    : "Unknown";
            String chunkId = meta.get("chunk_id") != null ? meta.get("chunk_id").toString()
                    : (meta.get("id") != null ? meta.get("id").toString() : "chunk_" + i);
            String text = d.getText();
            String excerpt = text.length() > 500 ? text.substring(0, 500) + "..." : text;
            citations.add(CitationDto.builder().chunkId(chunkId).source(source).excerpt(excerpt).build());
        }
        return citations;
    }

    // ==================== 私有：DB 操作 ====================

    private void finishAssistantMessage(String assistantMessageId, String content, String status,
            String errorCode, String errorMessage,
            Integer promptTokens, Integer completionTokens, Double estimatedCostUsd) {
        Message m = messageService.getById(assistantMessageId);
        if (m == null)
            return;
        m.setContent(content != null ? content : "");
        m.setStatus(status);
        m.setErrorCode(errorCode);
        m.setErrorMessage(errorMessage);
        m.setPromptTokens(promptTokens);
        m.setCompletionTokens(completionTokens);
        m.setEstimatedCostUsd(estimatedCostUsd);
        messageService.updateById(m);
    }

    // ==================== 私有：System Prompt ====================

    private static String buildSystemPrompt(String difficulty, String language) {
        String langInstruction = "zh".equalsIgnoreCase(language) ? "请使用中文回答。" : "Please answer in English.";
        String diffHint = switch (difficulty.toLowerCase()) {
            case "basic" -> "Use simple language and avoid jargon.";
            case "advanced" -> "You may use precise terminology and include formulas when helpful.";
            default -> "Explain clearly with moderate detail.";
        };
        String markerProtocol = " For key terms or symbols, use markers: [[term:Term Name]] or [[sym:formula]]. "
                + "Optional stable key: [[term:Name|key=id]]. "
                + "Do not use [[...]] for anything other than term/sym markers.";
        return "You are a university-level astronomy tutor. " + langInstruction + " " + diffHint
                + " Structure your answer: conclusion first, then layered explanation, optional formulas, "
                + "common misconceptions, and next-step suggestions. "
                + "Use Markdown and LaTeX where appropriate. Do not fabricate citations."
                + " If reference materials are provided in the context, prioritize them and cite sources."
                + " When no reference context is provided or the context is empty, answer directly from your general astronomical knowledge; do not refuse, do not ask the user to supply context, and do not say the context appears empty."
                + markerProtocol;
    }

    // ==================== 私有：工具方法 ====================

    private ServerSentEvent<String> sseError(String code, String message, String requestId) {
        Map<String, Object> errorMap = new HashMap<>(Map.of("code", code, "message", message));
        if (requestId != null)
            errorMap.put("requestId", requestId);
        return ServerSentEvent.<String>builder(toJson(Map.of("status", "error", "error", errorMap)))
                .event("error").build();
    }

    private String toJson(Map<String, ?> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }
}
