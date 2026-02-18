package com.imperium.astroguide.ai.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imperium.astroguide.ai.advisor.WikipediaOnDemandAdvisor;
import com.imperium.astroguide.ai.tools.ConceptCardTool;
import com.imperium.astroguide.ai.tools.KnowledgeBaseTool;
import com.imperium.astroguide.ai.tools.WikipediaTool;
import com.imperium.astroguide.model.dto.rag.CitationDto;
import com.imperium.astroguide.model.dto.rag.RagRetrieveResult;
import com.imperium.astroguide.model.entity.Conversation;
import com.imperium.astroguide.model.entity.Message;
import com.imperium.astroguide.policy.ContextTrimPolicy;
import com.imperium.astroguide.policy.OutputLimitPolicy;
import com.imperium.astroguide.policy.RateLimitPolicy;
import com.imperium.astroguide.service.ChatMemoryPrimeTracker;
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
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ChatStreamOrchestrator {

    /** 约 1 token ≈ 4 字符，用于用量估算 */
    private static final int CHARS_PER_TOKEN_ESTIMATE = 4;
    /** 估算成本（美元/1M tokens）- 示例 DeepSeek 档位，可配置 */
    private static final double COST_PER_MILLION_INPUT = 0.14;
    private static final double COST_PER_MILLION_OUTPUT = 0.28;

    private final ConversationService conversationService;
    private final MessageService messageService;
    private final ChatStreamService chatStreamService;
    private final UsageService usageService;
    private final RateLimitPolicy rateLimitPolicy;
    private final ObjectMapper objectMapper;
    private final ChatMemory chatMemory;
    private final ChatMemoryPrimeTracker chatMemoryPrimeTracker;

    private final WikipediaTool wikipediaTool;
    private final KnowledgeBaseTool knowledgeBaseTool;
    private final ConceptCardTool conceptCardTool;

    @Value("${spring.ai.openai.chat.options.model:deepseek-chat}")
    private String defaultModel;

    @Value("${app.rag.enabled:false}")
    private boolean ragEnabled;

    @Value("${app.rag.wikipedia-on-demand.enabled:false}")
    private boolean wikipediaOnDemandEnabled;

    /**
     * 是否启用 Spring AI 框架托管的 Tool Calling。
     * - true: 由 Spring AI 在生成过程中自动决定并执行 @Tool 方法
     * - false: 不注册 tools（仍可走 RAG/Wikipedia advisor）
     */
    @Value("${app.ai.tools.enabled:true}")
    private boolean toolsEnabled;

    public ChatStreamOrchestrator(ConversationService conversationService,
                                 MessageService messageService,
                                 ChatStreamService chatStreamService,
                                 UsageService usageService,
                                 RateLimitPolicy rateLimitPolicy,
                                 ObjectMapper objectMapper,
                                 ChatMemory chatMemory,
                                 ChatMemoryPrimeTracker chatMemoryPrimeTracker,
                                 WikipediaTool wikipediaTool,
                                 KnowledgeBaseTool knowledgeBaseTool,
                                 ConceptCardTool conceptCardTool) {
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.chatStreamService = chatStreamService;
        this.usageService = usageService;
        this.rateLimitPolicy = rateLimitPolicy;
        this.objectMapper = objectMapper;
        this.chatMemory = chatMemory;
        this.chatMemoryPrimeTracker = chatMemoryPrimeTracker;
        this.wikipediaTool = wikipediaTool;
        this.knowledgeBaseTool = knowledgeBaseTool;
        this.conceptCardTool = conceptCardTool;
    }

    public Flux<ServerSentEvent<String>> stream(String conversationId,
                                                String messageId,
                                                String clientId,
                                                HttpServletRequest request) {
        if (clientId == null || clientId.isBlank()) {
            return Flux.just(serverSentError("invalid_argument", "X-Client-Id is required", null));
        }

        String clientIp = request != null ? request.getRemoteAddr() : null;
        if (!rateLimitPolicy.allow(clientId, clientIp)) {
            return Flux.just(serverSentError("rate_limited", "Too many requests", null));
        }

        Conversation conversation = conversationService.getById(conversationId);
        if (conversation == null) {
            return Flux.just(serverSentError("not_found", "Conversation not found", null));
        }
        if (!Objects.equals(conversation.getClientId(), clientId)) {
            return Flux.just(serverSentError("forbidden", "clientId does not match conversation", null));
        }

        Message userMessage = messageService.getById(messageId);
        if (userMessage == null || !conversationId.equals(userMessage.getConversationId())) {
            return Flux.just(serverSentError("not_found", "Message not found", null));
        }
        if (!"user".equals(userMessage.getRole())) {
            return Flux.just(serverSentError("invalid_argument", "Message is not a user message", null));
        }

        String difficulty = userMessage.getDifficulty() != null ? userMessage.getDifficulty() : "intermediate";
        String language = userMessage.getLanguage() != null ? userMessage.getLanguage() : "en";
        String userQuestion = userMessage.getContent() != null ? userMessage.getContent() : "";

        // 将历史消息写入 ChatMemory（仍以 MySQL 为权威来源；ChatMemory 仅用于注入 prompt 上下文）
        primeChatMemoryFromDb(conversationId, userMessage);

        int maxTokens = OutputLimitPolicy.getMaxCompletionTokens(difficulty);
        String requestId = "req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        String assistantMessageId = messageId + "_a";
        Message assistantMessage = messageService.getById(assistantMessageId);
        if (assistantMessage != null) {
            assistantMessage.setStatus("streaming");
            messageService.updateById(assistantMessage);
        }

        String metaData = toJson(Map.of(
                "requestId", requestId,
                "model", defaultModel,
                "difficulty", difficulty,
                "language", language
        ));
        Flux<ServerSentEvent<String>> metaFlux = Flux.just(
                ServerSentEvent.<String>builder(metaData).event("meta").build()
        );

        long startMs = System.currentTimeMillis();
        StringBuilder contentAccumulator = new StringBuilder();

        AtomicReference<List<Document>> ragDocsRef = new AtomicReference<>();
        AtomicReference<List<Document>> wikiDocsRef = new AtomicReference<>();

        return metaFlux.concatWith(Flux.defer(() -> {
            // prompt token 估算：system + 当前 user + ChatMemory 历史（由 MemoryAdvisor 注入）
            int memoryCharsEst = estimateChatMemoryChars(conversationId);

            return Mono.fromCallable(() -> resolveManualDirective(userQuestion, language))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(manual -> {
                    String effectiveUserText = manual.augmentedUserText != null ? manual.augmentedUserText : userQuestion;

                    boolean toolCallingEnabled = toolsEnabled;
                    boolean ragEnabledForRun = ragEnabled;

                    // If user explicitly requested KB retrieval, we already injected references; skip auto RAG advisor to avoid duplicate retrieval cost.
                    if (manual.enabled && manual.tool == ManualTool.KB) {
                        ragEnabledForRun = false;
                    }

                    boolean wikipediaAdvisorEnabledForRun = wikipediaOnDemandEnabled && !toolCallingEnabled;

                    // Manual directive mode: deterministic prefetch + inject reference.
                    // Keep tool calling enabled (if configured) so the model can still call OTHER tools in the same run.
                    if (manual.enabled) {
                        if (manual.ragDocs != null && !manual.ragDocs.isEmpty()) {
                            ragDocsRef.set(manual.ragDocs);
                        }
                        if (manual.wikiDocs != null && !manual.wikiDocs.isEmpty()) {
                            wikiDocsRef.set(manual.wikiDocs);
                        }
                    }

                    boolean wikipediaToolEnabledForRun = true;
                    boolean knowledgeBaseToolEnabledForRun = true;
                    boolean conceptCardToolEnabledForRun = true;
                    if (manual.enabled) {
                        // Avoid re-invoking the same tool that was explicitly requested.
                        if (manual.tool == ManualTool.WIKI) {
                            wikipediaToolEnabledForRun = false;
                        } else if (manual.tool == ManualTool.KB) {
                            knowledgeBaseToolEnabledForRun = false;
                        } else if (manual.tool == ManualTool.CARD) {
                            conceptCardToolEnabledForRun = false;
                        }
                    }

                    boolean hasReference = ragEnabledForRun || wikipediaAdvisorEnabledForRun || toolCallingEnabled
                        || (manual.enabled && manual.hasReference);
                    String systemPrompt = buildSystemPrompt(difficulty, language, hasReference);

                    Map<String, Object> advisorParams = new HashMap<>();
                    advisorParams.put(WikipediaOnDemandAdvisor.ORIGINAL_QUERY,
                        manual.cleanedUserText != null ? manual.cleanedUserText : userQuestion);

                    Flux<ChatClientResponse> responseFlux = chatStreamService
                        .streamChatClientResponses(conversationId, systemPrompt, effectiveUserText, defaultModel, maxTokens,
                            ragEnabledForRun,
                            wikipediaAdvisorEnabledForRun,
                            toolCallingEnabled,
                            wikipediaToolEnabledForRun,
                            knowledgeBaseToolEnabledForRun,
                            conceptCardToolEnabledForRun,
                            advisorParams)
                        .doOnNext(r -> captureRetrievedDocuments(r, ragDocsRef, wikiDocsRef));

                    Flux<ServerSentEvent<String>> deltaFlux = responseFlux
                        .map(ChatStreamOrchestrator::extractDeltaText)
                        .filter(s -> s != null && !s.isBlank())
                        .doOnNext(contentAccumulator::append)
                        .map(chunk -> toJson(Map.of("text", chunk)))
                        .map(data -> ServerSentEvent.<String>builder(data).event("delta").build());

                    Flux<ServerSentEvent<String>> doneFlux = Flux.defer(() -> {
                        int latencyMs = (int) (System.currentTimeMillis() - startMs);
                        int promptTokensEst = estimateTokens(systemPrompt.length() + effectiveUserText.length() + memoryCharsEst);
                        int completionTokensEst = estimateTokens(contentAccumulator.length());
                        double costUsd = estimateCostUsd(promptTokensEst, completionTokensEst);
                        Map<String, Object> donePayload = new HashMap<>(Map.of(
                            "status", "done",
                            "usage", Map.of(
                                "promptTokens", promptTokensEst,
                                "completionTokens", completionTokensEst,
                                "estimatedCostUsd", costUsd
                            )
                        ));

                        List<CitationDto> citations = mergeCitations(ragDocsRef.get(), wikiDocsRef.get());
                        if (citations != null && !citations.isEmpty()) {
                            List<Map<String, String>> citationMaps = new ArrayList<>();
                            for (CitationDto c : citations) {
                                citationMaps.add(Map.of(
                                    "chunkId", c.getChunkId() != null ? c.getChunkId() : "",
                                    "source", c.getSource() != null ? c.getSource() : "",
                                    "excerpt", c.getExcerpt() != null ? c.getExcerpt() : ""
                                ));
                            }
                            donePayload.put("citations", citationMaps);
                        }

                        String doneData = toJson(donePayload);
                        finishAssistantMessage(assistantMessageId, contentAccumulator.toString(),
                            "done", null, null, promptTokensEst, completionTokensEst, costUsd);
                        usageService.record(assistantMessageId, defaultModel, latencyMs,
                            promptTokensEst, completionTokensEst, costUsd);
                        return Flux.just(ServerSentEvent.<String>builder(doneData).event("done").build());
                    }).subscribeOn(Schedulers.boundedElastic());

                    return deltaFlux
                        .concatWith(doneFlux)
                        .onErrorResume(e -> Flux.defer(() -> {
                            finishAssistantMessage(assistantMessageId, contentAccumulator.toString(),
                                "error", "provider_error", e.getMessage() != null ? e.getMessage() : "LLM stream failed",
                                null, null, null);
                            return Flux.just(serverSentError("provider_error",
                                e.getMessage() != null ? e.getMessage() : "LLM stream failed", requestId));
                        }).subscribeOn(Schedulers.boundedElastic()))
                        .doOnCancel(() -> Schedulers.boundedElastic().schedule(() ->
                            finishAssistantMessage(assistantMessageId, contentAccumulator.toString(),
                                "cancelled", null, null, null, null, null)));
                });
        }));
    }

    private enum ManualTool {
        NONE,
        WIKI,
        KB,
        CARD
    }

    private static final Pattern KB_TOPK_PATTERN = Pattern.compile("(?i)\\btopk\\s*=\\s*(\\d+)\\b");

    private record ManualDirectiveResult(
            boolean enabled,
            ManualTool tool,
            String cleanedUserText,
            String augmentedUserText,
            boolean hasReference,
            List<Document> ragDocs,
            List<Document> wikiDocs
    ) {
        static ManualDirectiveResult none(String original) {
            return new ManualDirectiveResult(false, ManualTool.NONE, original, null, false, null, null);
        }
    }

    private ManualDirectiveResult resolveManualDirective(String originalUserText, String language) {
        if (originalUserText == null) {
            return ManualDirectiveResult.none("");
        }

        String text = originalUserText.trim();
        if (text.isEmpty()) {
            return ManualDirectiveResult.none("");
        }

        if (startsWithIgnoreCase(text, "@wiki:")) {
            String q = text.substring("@wiki:".length()).trim();
            if (q.isEmpty()) {
                return ManualDirectiveResult.none(originalUserText);
            }
            RagRetrieveResult wiki = wikipediaTool.searchWikipedia(q);
            String ref = wiki != null && wiki.getReferenceText() != null ? wiki.getReferenceText().trim() : "";
            List<Document> docs = toDocuments(wiki);
            String augmented = buildAugmentedUserText("参考", ref, q);
            return new ManualDirectiveResult(true, ManualTool.WIKI, q, augmented,
                !ref.isBlank() || !docs.isEmpty(), null, docs);
        }

        if (startsWithIgnoreCase(text, "@kb:")) {
            String q0 = text.substring("@kb:".length()).trim();
            if (q0.isEmpty()) {
                return ManualDirectiveResult.none(originalUserText);
            }

            Integer topK = null;
            Matcher m = KB_TOPK_PATTERN.matcher(q0);
            if (m.find()) {
                try {
                    topK = Integer.parseInt(m.group(1));
                } catch (Exception ignored) {
                    topK = null;
                }
                q0 = (q0.substring(0, m.start()) + " " + q0.substring(m.end())).trim();
            }
            String q = q0;
            RagRetrieveResult kb = knowledgeBaseTool.searchKnowledgeBase(q, topK);
            String ref = kb != null && kb.getReferenceText() != null ? kb.getReferenceText().trim() : "";
            List<Document> docs = toDocuments(kb);
            String augmented = buildAugmentedUserText("参考", ref, q);
            return new ManualDirectiveResult(true, ManualTool.KB, q, augmented,
                !ref.isBlank() || !docs.isEmpty(), docs, null);
        }

        if (startsWithIgnoreCase(text, "@card:")) {
            String payload = text.substring("@card:".length()).trim();
            if (payload.isEmpty()) {
                return ManualDirectiveResult.none(originalUserText);
            }

            CardArgs args = parseCardArgs(payload, language);
            Map<String, Object> card = conceptCardTool.lookupConceptCard(args.type, args.lang, args.key, args.text);
            String json;
            try {
                json = objectMapper.writeValueAsString(card);
            } catch (Exception ignored) {
                json = String.valueOf(card);
            }

            String cleaned = args.text != null && !args.text.isBlank() ? args.text : payload;
            String augmented = buildAugmentedUserText("概念卡", json, cleaned);
            boolean found = card != null && Boolean.TRUE.equals(card.get("found"));
            return new ManualDirectiveResult(true, ManualTool.CARD, cleaned, augmented,
                found || (json != null && !json.isBlank()), null, null);
        }

        return ManualDirectiveResult.none(originalUserText);
    }

    private static boolean startsWithIgnoreCase(String text, String prefix) {
        if (text == null || prefix == null) return false;
        if (text.length() < prefix.length()) return false;
        return text.regionMatches(true, 0, prefix, 0, prefix.length());
    }

    private static String buildAugmentedUserText(String title, String referenceText, String question) {
        String ref = referenceText != null ? referenceText.trim() : "";
        String q = question != null ? question : "";
        if (ref.isBlank()) {
            return q;
        }
        return "[" + title + "]\n\n" + ref + "\n\n---\n\n" + q;
    }

    private static List<Document> toDocuments(RagRetrieveResult result) {
        List<Document> docs = new ArrayList<>();
        if (result == null || result.getCitations() == null) {
            return docs;
        }
        for (CitationDto c : result.getCitations()) {
            if (c == null) continue;
            String t = c.getExcerpt() != null ? c.getExcerpt() : "";
            if (t.isBlank()) continue;
            Map<String, Object> meta = new HashMap<>();
            if (c.getSource() != null) meta.put("source", c.getSource());
            if (c.getChunkId() != null) meta.put("chunk_id", c.getChunkId());
            docs.add(new Document(t, meta));
        }
        return docs;
    }

    private record CardArgs(String type, String lang, String key, String text) {}

    private static CardArgs parseCardArgs(String payload, String defaultLang) {
        String type = "term";
        String lang = (defaultLang == null || defaultLang.isBlank()) ? "en" : defaultLang.trim().toLowerCase();
        String key = null;
        String text = payload;

        String lower = payload.toLowerCase();
        if (lower.contains("type=") || lower.contains("lang=") || lower.contains("key=") || lower.contains("text=")) {
            type = extractKv(payload, "type", type);
            lang = extractKv(payload, "lang", lang);
            key = extractKvNullable(payload, "key");
            String extractedText = extractTextKv(payload);
            if (extractedText != null && !extractedText.isBlank()) {
                text = extractedText;
            }
        } else {
            // shorthand: "term zh 黑洞" / "zh 黑洞" / "黑洞"
            String[] parts = payload.trim().split("\\s+", 3);
            if (parts.length >= 1) {
                if ("term".equalsIgnoreCase(parts[0]) || "sym".equalsIgnoreCase(parts[0])) {
                    type = parts[0].toLowerCase();
                    if (parts.length >= 2 && ("zh".equalsIgnoreCase(parts[1]) || "en".equalsIgnoreCase(parts[1]))) {
                        lang = parts[1].toLowerCase();
                        if (parts.length == 3) {
                            text = parts[2];
                        }
                    } else if (parts.length >= 2) {
                        text = payload.substring(parts[0].length()).trim();
                    }
                } else if ("zh".equalsIgnoreCase(parts[0]) || "en".equalsIgnoreCase(parts[0])) {
                    lang = parts[0].toLowerCase();
                    if (parts.length >= 2) {
                        text = payload.substring(parts[0].length()).trim();
                    }
                }
            }
        }

        if (!"term".equalsIgnoreCase(type) && !"sym".equalsIgnoreCase(type)) {
            type = "term";
        }
        if (!"zh".equalsIgnoreCase(lang) && !"en".equalsIgnoreCase(lang)) {
            lang = "en";
        }
        if (text != null) {
            text = stripQuotes(text.trim());
        }
        if (key != null) {
            key = stripQuotes(key.trim());
            if (key.isBlank()) key = null;
        }
        return new CardArgs(type, lang, key, text);
    }

    private static String extractKv(String payload, String key, String defaultValue) {
        String v = extractKvNullable(payload, key);
        if (v == null || v.isBlank()) {
            return defaultValue;
        }
        return stripQuotes(v.trim());
    }

    private static String extractKvNullable(String payload, String key) {
        String lower = payload.toLowerCase();
        int idx = lower.indexOf(key.toLowerCase() + "=");
        if (idx < 0) return null;
        int start = idx + key.length() + 1;

        // read until whitespace
        int end = payload.length();
        for (int i = start; i < payload.length(); i++) {
            char ch = payload.charAt(i);
            if (Character.isWhitespace(ch)) {
                end = i;
                break;
            }
        }
        return payload.substring(start, end);
    }

    private static String extractTextKv(String payload) {
        String lower = payload.toLowerCase();
        int idx = lower.indexOf("text=");
        if (idx < 0) return null;
        String v = payload.substring(idx + "text=".length()).trim();
        return stripQuotes(v);
    }

    private static String stripQuotes(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.length() >= 2 && ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'")))) {
            return t.substring(1, t.length() - 1);
        }
        return t;
    }

    private void primeChatMemoryFromDb(String conversationId, Message currentUserMessage) {
        if (conversationId == null || conversationId.isBlank() || currentUserMessage == null) {
            return;
        }

        if (currentUserMessage.getCreatedAt() == null) {
            return;
        }

        var existingMemory = chatMemory.get(conversationId);
        ChatMemoryPrimeTracker.PrimeCursor cursor = chatMemoryPrimeTracker.getCursor(conversationId);
        boolean needRebuild = cursor == null
                || existingMemory == null
                || existingMemory.isEmpty()
                || currentUserMessage.getCreatedAt().isBefore(cursor.createdAt());

        if (needRebuild) {
            chatMemory.clear(conversationId);
            chatMemoryPrimeTracker.clearCursor(conversationId);
            rebuildChatMemoryFromDb(conversationId, currentUserMessage);
            return;
        }

        incrementalPrimeChatMemoryFromDb(conversationId, currentUserMessage, cursor);
    }

    private void rebuildChatMemoryFromDb(String conversationId, Message currentUserMessage) {
        List<com.imperium.astroguide.model.entity.Message> history = messageService.lambdaQuery()
                .eq(com.imperium.astroguide.model.entity.Message::getConversationId, conversationId)
                .lt(com.imperium.astroguide.model.entity.Message::getCreatedAt, currentUserMessage.getCreatedAt())
                .orderByAsc(com.imperium.astroguide.model.entity.Message::getCreatedAt)
                .orderByAsc(com.imperium.astroguide.model.entity.Message::getId)
                .last("LIMIT " + (2 * ContextTrimPolicy.DEFAULT_MAX_ROUNDS))
                .list();

        List<org.springframework.ai.chat.messages.Message> memoryMessages = new ArrayList<>();
        com.imperium.astroguide.model.entity.Message lastSeen = null;

        for (com.imperium.astroguide.model.entity.Message m : history) {
            if (m == null) continue;
            lastSeen = m;
            String role = m.getRole();
            String content = m.getContent() != null ? m.getContent() : "";

            if ("assistant".equals(role)) {
                if (content.isBlank()) continue;
                if (m.getStatus() != null && "queued".equalsIgnoreCase(m.getStatus())) continue;
                memoryMessages.add(new AssistantMessage(content));
            } else {
                if (content.isBlank()) continue;
                memoryMessages.add(new UserMessage(content));
            }
        }

        if (!memoryMessages.isEmpty()) {
            chatMemory.add(conversationId, memoryMessages);
        }

        if (lastSeen != null && lastSeen.getCreatedAt() != null) {
            chatMemoryPrimeTracker.updateCursor(conversationId, lastSeen.getCreatedAt(), lastSeen.getId());
        }
    }

    private void incrementalPrimeChatMemoryFromDb(String conversationId,
                                                  Message currentUserMessage,
                                                  ChatMemoryPrimeTracker.PrimeCursor cursor) {
        List<com.imperium.astroguide.model.entity.Message> delta = messageService.lambdaQuery()
                .eq(com.imperium.astroguide.model.entity.Message::getConversationId, conversationId)
                .apply("(created_at > {0} OR (created_at = {0} AND id > {1}))",
                        cursor.createdAt(), cursor.messageId())
                .lt(com.imperium.astroguide.model.entity.Message::getCreatedAt, currentUserMessage.getCreatedAt())
                .orderByAsc(com.imperium.astroguide.model.entity.Message::getCreatedAt)
                .orderByAsc(com.imperium.astroguide.model.entity.Message::getId)
                .last("LIMIT " + (2 * ContextTrimPolicy.DEFAULT_MAX_ROUNDS))
                .list();

        if (delta == null || delta.isEmpty()) {
            return;
        }

        List<org.springframework.ai.chat.messages.Message> memoryMessages = new ArrayList<>();
        com.imperium.astroguide.model.entity.Message lastSeen = null;

        for (com.imperium.astroguide.model.entity.Message m : delta) {
            if (m == null) continue;
            lastSeen = m;
            String role = m.getRole();
            String content = m.getContent() != null ? m.getContent() : "";

            if ("assistant".equals(role)) {
                if (content.isBlank()) continue;
                if (m.getStatus() != null && "queued".equalsIgnoreCase(m.getStatus())) continue;
                memoryMessages.add(new AssistantMessage(content));
            } else {
                if (content.isBlank()) continue;
                memoryMessages.add(new UserMessage(content));
            }
        }

        if (!memoryMessages.isEmpty()) {
            chatMemory.add(conversationId, memoryMessages);
        }

        if (lastSeen != null && lastSeen.getCreatedAt() != null) {
            chatMemoryPrimeTracker.updateCursor(conversationId, lastSeen.getCreatedAt(), lastSeen.getId());
        }
    }

    private int estimateChatMemoryChars(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return 0;
        }
        List<org.springframework.ai.chat.messages.Message> messages = chatMemory.get(conversationId);
        if (messages == null || messages.isEmpty()) {
            return 0;
        }

        int total = 0;
        for (org.springframework.ai.chat.messages.Message m : messages) {
            if (m == null) continue;
            String text = m.getText();
            if (text != null) {
                total += text.length();
            }
            total += 12;
        }
        return total;
    }

    private void finishAssistantMessage(String assistantMessageId, String content, String status,
                                        String errorCode, String errorMessage,
                                        Integer promptTokens, Integer completionTokens, Double estimatedCostUsd) {
        Message m = messageService.getById(assistantMessageId);
        if (m == null) return;
        m.setContent(content != null ? content : "");
        m.setStatus(status);
        m.setErrorCode(errorCode);
        m.setErrorMessage(errorMessage);
        m.setPromptTokens(promptTokens);
        m.setCompletionTokens(completionTokens);
        m.setEstimatedCostUsd(estimatedCostUsd);
        messageService.updateById(m);
    }

    private static void captureRetrievedDocuments(ChatClientResponse response,
                                                 AtomicReference<List<Document>> ragDocsRef,
                                                 AtomicReference<List<Document>> wikiDocsRef) {
        if (response == null || response.context() == null) {
            return;
        }
        if (ragDocsRef.get() == null) {
            Object ragObj = response.context().get(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS);
            if (ragObj instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Document) {
                @SuppressWarnings("unchecked")
                List<Document> docs = (List<Document>) list;
                ragDocsRef.set(docs);
            }
        }
        if (wikiDocsRef.get() == null) {
            Object wikiObj = response.context().get(WikipediaOnDemandAdvisor.RETRIEVED_DOCUMENTS);
            if (wikiObj instanceof List<?> list && !list.isEmpty() && list.get(0) instanceof Document) {
                @SuppressWarnings("unchecked")
                List<Document> docs = (List<Document>) list;
                wikiDocsRef.set(docs);
            }
        }
    }

    private static String extractDeltaText(ChatClientResponse response) {
        if (response == null || response.chatResponse() == null
                || response.chatResponse().getResult() == null
                || response.chatResponse().getResult().getOutput() == null) {
            return null;
        }
        return response.chatResponse().getResult().getOutput().getText();
    }

    private static List<CitationDto> mergeCitations(List<Document> ragDocs, List<Document> wikiDocs) {
        List<CitationDto> out = new ArrayList<>();
        if (ragDocs != null) {
            out.addAll(toCitations(ragDocs, out.size()));
        }
        if (wikiDocs != null) {
            out.addAll(toCitations(wikiDocs, out.size()));
        }
        return out;
    }

    private static List<CitationDto> toCitations(List<Document> docs, int startIndex) {
        List<CitationDto> citations = new ArrayList<>();
        int idx = startIndex;
        for (Document d : docs) {
            if (d == null || d.getText() == null || d.getText().isBlank()) continue;
            Map<String, Object> meta = d.getMetadata();
            String source = meta != null && meta.get("source") != null ? meta.get("source").toString() : "Unknown";
            String chunkId = meta != null && meta.get("chunk_id") != null ? meta.get("chunk_id").toString() : null;
            if (chunkId == null && meta != null && meta.get("id") != null) {
                chunkId = meta.get("id").toString();
            }
            if (chunkId == null) {
                chunkId = "chunk_" + idx;
            }
            String text = d.getText();
            String excerpt = text.length() > 500 ? text.substring(0, 500) + "..." : text;
            citations.add(CitationDto.builder()
                    .chunkId(chunkId)
                    .source(source)
                    .excerpt(excerpt)
                    .build());
            idx++;
        }
        return citations;
    }

    private static int estimateTokens(int chars) {
        return Math.max(0, (chars + CHARS_PER_TOKEN_ESTIMATE - 1) / CHARS_PER_TOKEN_ESTIMATE);
    }

    private static double estimateCostUsd(int promptTokens, int completionTokens) {
        return (promptTokens * COST_PER_MILLION_INPUT + completionTokens * COST_PER_MILLION_OUTPUT) / 1_000_000.0;
    }

    private String buildSystemPrompt(String difficulty, String language, boolean hasReference) {
        String langInstruction = "zh".equalsIgnoreCase(language)
                ? "请使用中文回答。"
                : "Please answer in English.";
        String diffHint = switch (difficulty.toLowerCase()) {
            case "basic" -> "Use simple language and avoid jargon.";
            case "advanced" -> "You may use precise terminology and include formulas when helpful.";
            default -> "Explain clearly with moderate detail.";
        };
        String markerProtocol = " For key terms or symbols, use markers: [[term:Term Name]] or [[sym:formula]]. "
                + "Optional stable key: [[term:Name|key=id]]. "
                + "Do not use [[...]] for anything other than term/sym markers.";
        String refInstruction = hasReference
                ? " Prioritize the reference content below when answering; you may cite [来源: xxx] where appropriate. Do not invent information not present in the references."
                : "";
        return "You are a university-level astronomy tutor. " + langInstruction + " " + diffHint
                + " Structure your answer: conclusion first, then layered explanation, optional formulas, common misconceptions, and next-step suggestions. "
                + "Use Markdown and LaTeX where appropriate. Do not fabricate citations." + refInstruction + markerProtocol;
    }

    private ServerSentEvent<String> serverSentError(String code, String message, String requestId) {
        Map<String, Object> errorMap = new HashMap<>(Map.of("code", code, "message", message));
        if (requestId != null) errorMap.put("requestId", requestId);
        String data = toJson(Map.of("status", "error", "error", errorMap));
        return ServerSentEvent.<String>builder(data).event("error").build();
    }

    private String toJson(Map<String, ?> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }
}
