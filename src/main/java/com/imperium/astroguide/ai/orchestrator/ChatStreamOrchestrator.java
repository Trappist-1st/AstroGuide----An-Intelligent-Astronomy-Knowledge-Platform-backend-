package com.imperium.astroguide.ai.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.imperium.astroguide.ai.memory.MemoryUpdateQueue;
import com.imperium.astroguide.ai.memory.SessionMemoryService;
import com.imperium.astroguide.ai.memory.SummaryMemoryService;
import com.imperium.astroguide.ai.runtime.AgentRunCancellationRegistry;
import com.imperium.astroguide.ai.runtime.AgentRunRequest;
import com.imperium.astroguide.ai.runtime.AgentRunResult;
import com.imperium.astroguide.ai.runtime.AgentRuntime;
import com.imperium.astroguide.ai.runtime.AgentStreamEvent;
import com.imperium.astroguide.ai.tool.ToolExecutionRecord;
import com.imperium.astroguide.infra.coordination.DistributedLock;
import com.imperium.astroguide.model.dto.rag.CitationDto;
import com.imperium.astroguide.model.entity.Conversation;
import com.imperium.astroguide.policy.OutputLimitPolicy;
import com.imperium.astroguide.policy.RateLimitPolicy;
import com.imperium.astroguide.service.ConversationService;
import com.imperium.astroguide.service.MessageService;
import com.imperium.astroguide.service.UsageService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * SSE 流式对话编排器。
 * <p>
 * 职责：请求验证 → 限流 → 加载历史 → 调用 {@link AgentRuntime}（LangGraph）→ 落库 → SSE 事件组装。
 */
@Service
public class ChatStreamOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(ChatStreamOrchestrator.class);

    private final ConversationService conversationService;
    private final MessageService messageService;
    private final AgentRuntime agentRuntime;
    private final UsageService usageService;
    private final RateLimitPolicy rateLimitPolicy;
    private final ObjectMapper objectMapper;
    private final SessionMemoryService sessionMemoryService;
    private final SummaryMemoryService summaryMemoryService;
    private final MemoryUpdateQueue memoryUpdateQueue;
    private final MeterRegistry meterRegistry;
    private final AgentRunCancellationRegistry cancellationRegistry;
    private final DistributedLock distributedLock;

    @Value("${app.ai.stream.lock-ttl-ms:600000}")
    private long streamLockTtlMs;

    @Value("${spring.ai.openai.chat.options.model:deepseek-chat}")
    private String defaultModel;

    @Value("${app.ai.runtime.emit-node-events:true}")
    private boolean emitNodeEvents;

    @Value("${app.ai.runtime.emit-tool-events:true}")
    private boolean emitToolEvents;

    @Value("${app.ai.runtime.emit-route-events:true}")
    private boolean emitRouteEvents;

    public ChatStreamOrchestrator(ConversationService conversationService,
            MessageService messageService,
            AgentRuntime agentRuntime,
            UsageService usageService,
            RateLimitPolicy rateLimitPolicy,
            ObjectMapper objectMapper,
            SessionMemoryService sessionMemoryService,
            SummaryMemoryService summaryMemoryService,
            MemoryUpdateQueue memoryUpdateQueue,
            MeterRegistry meterRegistry,
            AgentRunCancellationRegistry cancellationRegistry,
            DistributedLock distributedLock) {
        this.conversationService = conversationService;
        this.messageService = messageService;
        this.agentRuntime = agentRuntime;
        this.usageService = usageService;
        this.rateLimitPolicy = rateLimitPolicy;
        this.objectMapper = objectMapper;
        this.sessionMemoryService = sessionMemoryService;
        this.summaryMemoryService = summaryMemoryService;
        this.memoryUpdateQueue = memoryUpdateQueue;
        this.meterRegistry = meterRegistry;
        this.cancellationRegistry = cancellationRegistry;
        this.distributedLock = distributedLock;
    }

    public Flux<ServerSentEvent<String>> stream(String conversationId,
            String messageId,
            String clientId,
            String requestId,
            HttpServletRequest request) {

        String resolvedRequestId = normalizeRequestId(requestId);
        String runId = newRunId();

        ServerSentEvent<String> error = validate(conversationId, messageId, clientId, request, resolvedRequestId);
        if (error != null) {
            return Flux.just(error);
        }

        incrementCounter("astroguide.chat.stream.request", "status", "accepted");
        log.info("chat stream accepted requestId={} runId={} conversationId={} messageId={} clientId={}",
                resolvedRequestId, runId, conversationId, messageId, clientId);

        Conversation conversation = conversationService.getById(conversationId);
        if (conversation == null) {
            incrementCounter("astroguide.chat.stream.request", "status", "not_found");
            return Flux.just(sseError("not_found", "Conversation not found", resolvedRequestId));
        }
        if (!Objects.equals(conversation.getClientId(), clientId)) {
            incrementCounter("astroguide.chat.stream.request", "status", "forbidden");
            return Flux.just(sseError("forbidden", "clientId does not match conversation", resolvedRequestId));
        }

        com.imperium.astroguide.model.entity.Message userMessage = messageService.getById(messageId);
        if (userMessage == null || !conversationId.equals(userMessage.getConversationId())) {
            incrementCounter("astroguide.chat.stream.request", "status", "not_found");
            return Flux.just(sseError("not_found", "Message not found", resolvedRequestId));
        }
        if (!"user".equals(userMessage.getRole())) {
            incrementCounter("astroguide.chat.stream.request", "status", "invalid_argument");
            return Flux.just(sseError("invalid_argument", "Message is not a user message", resolvedRequestId));
        }

        String difficulty = userMessage.getDifficulty() != null ? userMessage.getDifficulty() : "intermediate";
        String language = userMessage.getLanguage() != null ? userMessage.getLanguage() : "en";
        String userQuestion = userMessage.getContent() != null ? userMessage.getContent() : "";
        int maxTokens = OutputLimitPolicy.getMaxCompletionTokens(difficulty);
        String systemPrompt = buildSystemPrompt(difficulty, language);
        String assistantMessageId = messageId + "_a";
        List<Message> historyMessages = sessionMemoryService.loadHistory(conversationId, userMessage);
        String conversationSummary = summaryMemoryService.loadSummary(conversationId);

        com.imperium.astroguide.model.entity.Message assistantMsg = messageService.getById(assistantMessageId);
        if (assistantMsg != null && isTerminalMessageStatus(assistantMsg.getStatus())) {
            incrementCounter("astroguide.chat.stream.request", "status", "conflict");
            return Flux.just(sseError("conflict", "Assistant message already finalized", resolvedRequestId));
        }

        String streamLockKey = "stream:" + assistantMessageId;
        if (!distributedLock.tryLock(streamLockKey, streamLockTtlMs)) {
            incrementCounter("astroguide.chat.stream.request", "status", "conflict");
            return Flux.just(sseError("conflict", "Stream already in progress for this message", resolvedRequestId));
        }

        if (assistantMsg != null) {
            assistantMsg.setStatus("streaming");
            messageService.updateById(assistantMsg);
        }

        AgentRunRequest runRequest = new AgentRunRequest(
                runId,
                resolvedRequestId,
                conversationId,
                messageId,
                systemPrompt,
                userQuestion,
                historyMessages,
                maxTokens,
                List.of(),
                conversationSummary);

        Flux<ServerSentEvent<String>> metaFlux = Flux.just(
                ServerSentEvent.builder(toJson(Map.of(
                        "requestId", resolvedRequestId,
                        "runId", runId,
                        "conversationId", conversationId,
                        "messageId", messageId,
                        "model", defaultModel,
                        "difficulty", difficulty,
                        "language", language,
                        "runtime", "langgraph"))).event("meta").build());

        long startMs = System.currentTimeMillis();
        StreamContentBuffer contentBuffer = new StreamContentBuffer();
        AtomicReference<AgentRunResult> runResultRef = new AtomicReference<>();
        AtomicReference<String> reviewOverrideRef = new AtomicReference<>();
        StreamTerminalState terminalState = new StreamTerminalState();

        return metaFlux.concatWith(Flux.defer(() -> {
            Flux<ServerSentEvent<String>> runtimeFlux = agentRuntime.stream(runRequest)
                    .flatMap(event -> mapRuntimeEvent(event, contentBuffer, runResultRef, reviewOverrideRef));

            Flux<ServerSentEvent<String>> doneFlux = Flux.defer(() -> {
                if (!terminalState.tryFinalize(StreamTerminalState.Status.DONE)) {
                    return Flux.empty();
                }
                int latencyMs = (int) (System.currentTimeMillis() - startMs);
                AgentRunResult result = runResultRef.get();
                String finalContent = resolveFinalContent(contentBuffer, reviewOverrideRef, result);
                Integer promptTok = result != null ? result.promptTokens() : null;
                Integer completionTok = result != null ? result.completionTokens() : null;
                List<CitationDto> citations = result != null && result.citations() != null
                        ? result.citations()
                        : List.of();

                Map<String, Object> donePayload = new HashMap<>(Map.of("status", "done", "runId", runId));
                if (promptTok != null || completionTok != null) {
                    Map<String, Object> usage = new HashMap<>();
                    if (promptTok != null) {
                        usage.put("promptTokens", promptTok);
                    }
                    if (completionTok != null) {
                        usage.put("completionTokens", completionTok);
                    }
                    donePayload.put("usage", usage);
                }
                if (!citations.isEmpty()) {
                    donePayload.put("citations", citations.stream().map(c -> Map.of(
                            "chunkId", c.getChunkId() != null ? c.getChunkId() : "",
                            "source", c.getSource() != null ? c.getSource() : "",
                            "excerpt", c.getExcerpt() != null ? c.getExcerpt() : "")).toList());
                }
                if (result != null && result.toolExecutions() != null && !result.toolExecutions().isEmpty()) {
                    donePayload.put("toolCalls", result.toolExecutions().size());
                }
                if (result != null && result.estimatedInputTokens() != null) {
                    donePayload.put("estimatedInputTokens", result.estimatedInputTokens());
                }
                if (result != null && result.routeMode() != null) {
                    donePayload.put("route", Map.of(
                            "mode", result.routeMode(),
                            "reason", result.routeReason() != null ? result.routeReason() : ""));
                }
                if (result != null && result.reviewPassed() != null) {
                    donePayload.put("review", Map.of(
                            "passed", result.reviewPassed(),
                            "reason", result.reviewReason() != null ? result.reviewReason() : ""));
                }

                memoryUpdateQueue.enqueue(conversationId);

                finishAssistantMessage(assistantMessageId, finalContent,
                        "done", null, null, promptTok, completionTok, null);
                usageService.record(assistantMessageId, defaultModel, latencyMs, promptTok, completionTok, null);
                recordTerminalMetrics(terminalState, "done", difficulty, latencyMs);
                log.info("chat stream done requestId={} runId={} conversationId={} messageId={} latencyMs={} toolCalls={}",
                        resolvedRequestId, runId, conversationId, messageId, latencyMs,
                        result != null && result.toolExecutions() != null ? result.toolExecutions().size() : 0);

                return Flux.just(ServerSentEvent.<String>builder(toJson(donePayload)).event("done").build());
            }).subscribeOn(Schedulers.boundedElastic());

            return runtimeFlux
                    .concatWith(doneFlux)
                    .onErrorResume(e -> Flux.defer(() -> {
                        if (!terminalState.tryFinalize(StreamTerminalState.Status.ERROR)) {
                            return Flux.empty();
                        }
                        finishAssistantMessage(assistantMessageId, contentBuffer.snapshot(),
                                "error", "provider_error",
                                sanitizeClientErrorMessage(e),
                                null, null, null);
                        int latencyMs = (int) (System.currentTimeMillis() - startMs);
                        recordTerminalMetrics(terminalState, "error", difficulty, latencyMs);
                        log.warn("chat stream error requestId={} runId={} conversationId={} messageId={} latencyMs={} error={}",
                                resolvedRequestId, runId, conversationId, messageId, latencyMs, e.getMessage());
                        return Flux.just(sseError("provider_error",
                                sanitizeClientErrorMessage(e), resolvedRequestId));
                    }).subscribeOn(Schedulers.boundedElastic()))
                    .doOnCancel(() -> Schedulers.boundedElastic().schedule(() -> {
                        cancellationRegistry.cancel(runId);
                        if (!terminalState.tryFinalize(StreamTerminalState.Status.CANCELLED)) {
                            return;
                        }
                        finishAssistantMessage(assistantMessageId, contentBuffer.snapshot(),
                                "cancelled", null, null, null, null, null);
                        int latencyMs = (int) (System.currentTimeMillis() - startMs);
                        recordTerminalMetrics(terminalState, "cancelled", difficulty, latencyMs);
                        log.info("chat stream cancelled requestId={} runId={} conversationId={} messageId={} latencyMs={}",
                                resolvedRequestId, runId, conversationId, messageId, latencyMs);
                    }));
        })).doFinally(signal -> distributedLock.unlock(streamLockKey));
    }

    private static boolean isTerminalMessageStatus(String status) {
        return "done".equals(status) || "error".equals(status) || "cancelled".equals(status);
    }

    private Flux<ServerSentEvent<String>> mapRuntimeEvent(AgentStreamEvent event,
            StreamContentBuffer contentBuffer,
            AtomicReference<AgentRunResult> runResultRef,
            AtomicReference<String> reviewOverrideRef) {
        if (event instanceof AgentStreamEvent.TextDelta delta) {
            if (delta.text() == null || delta.text().isBlank()) {
                return Flux.empty();
            }
            contentBuffer.append(delta.text());
            return Flux.just(ServerSentEvent.<String>builder(
                    toJson(Map.of("text", delta.text()))).event("delta").build());
        }
        if (event instanceof AgentStreamEvent.RunFinished finished) {
            runResultRef.set(finished.result());
            return Flux.empty();
        }
        if (event instanceof AgentStreamEvent.ReviewCompleted review
                && review.finalTextOverride() != null && !review.finalTextOverride().isBlank()) {
            reviewOverrideRef.set(review.finalTextOverride());
        }
        if (emitNodeEvents && event instanceof AgentStreamEvent.NodeStarted nodeStarted) {
            return Flux.just(ServerSentEvent.<String>builder(
                    toJson(Map.of("node", nodeStarted.nodeId()))).event("node_start").build());
        }
        if (emitNodeEvents && event instanceof AgentStreamEvent.NodeFinished nodeFinished) {
            return Flux.just(ServerSentEvent.<String>builder(toJson(Map.of(
                    "node", nodeFinished.nodeId(),
                    "latencyMs", nodeFinished.latencyMs()))).event("node_done").build());
        }
        if (emitToolEvents && event instanceof AgentStreamEvent.ToolStarted toolStarted) {
            return Flux.just(ServerSentEvent.<String>builder(toJson(Map.of(
                    "tool", toolStarted.toolName(),
                    "arguments", toolStarted.arguments() != null ? toolStarted.arguments() : ""))).event("tool_start").build());
        }
        if (emitToolEvents && event instanceof AgentStreamEvent.ToolFinished toolFinished) {
            ToolExecutionRecord record = toolFinished.record();
            return Flux.just(ServerSentEvent.<String>builder(toJson(Map.of(
                    "tool", record.getToolName(),
                    "success", record.isSuccess(),
                    "latencyMs", record.getLatencyMs()))).event("tool_done").build());
        }
        if (emitRouteEvents && event instanceof AgentStreamEvent.RouteSelected route) {
            return Flux.just(ServerSentEvent.<String>builder(toJson(Map.of(
                    "mode", route.mode(),
                    "reasonCode", route.reasonCode(),
                    "confidence", route.confidence()))).event("route").build());
        }
        if (emitRouteEvents && event instanceof AgentStreamEvent.ReviewCompleted review) {
            Map<String, Object> payload = new HashMap<>(Map.of(
                    "passed", review.passed(),
                    "reasonCode", review.reasonCode()));
            if (review.finalTextOverride() != null) {
                payload.put("revised", true);
            }
            return Flux.just(ServerSentEvent.<String>builder(toJson(payload)).event("review").build());
        }
        return Flux.empty();
    }

    private static String resolveFinalContent(StreamContentBuffer contentBuffer,
            AtomicReference<String> reviewOverrideRef,
            AgentRunResult result) {
        if (reviewOverrideRef.get() != null && !reviewOverrideRef.get().isBlank()) {
            return reviewOverrideRef.get();
        }
        if (result != null && result.finalText() != null && !result.finalText().isBlank()) {
            return result.finalText();
        }
        return contentBuffer.snapshot();
    }

    private static String sanitizeClientErrorMessage(Throwable e) {
        if (e == null || e.getMessage() == null || e.getMessage().isBlank()) {
            return "Agent runtime failed";
        }
        String msg = e.getMessage();
        if (msg.length() > 200) {
            return msg.substring(0, 200);
        }
        return msg;
    }

    private ServerSentEvent<String> validate(String conversationId, String messageId,
            String clientId, HttpServletRequest request, String requestId) {
        if (clientId == null || clientId.isBlank()) {
            incrementCounter("astroguide.chat.stream.request", "status", "invalid_argument");
            return sseError("invalid_argument", "X-Client-Id is required", requestId);
        }

        String clientIp = request != null ? request.getRemoteAddr() : null;
        if (!rateLimitPolicy.allow(clientId, clientIp)) {
            incrementCounter("astroguide.chat.stream.request", "status", "rate_limited");
            return sseError("rate_limited", "Too many requests", requestId);
        }

        return null;
    }

    private void recordTerminalMetrics(StreamTerminalState terminalState,
            String status,
            String difficulty,
            int latencyMs) {
        incrementCounter("astroguide.chat.stream.terminal", "status", status);
        Timer.builder("astroguide.chat.stream.latency")
                .tag("status", status)
                .tag("difficulty", difficulty)
                .register(meterRegistry)
                .record(latencyMs, TimeUnit.MILLISECONDS);
    }

    private void incrementCounter(String metricName, String... tags) {
        meterRegistry.counter(metricName, tags).increment();
    }

    private String normalizeRequestId(String requestId) {
        if (requestId != null && !requestId.isBlank()) {
            return requestId;
        }
        return "req_unknown";
    }

    private static String newRunId() {
        return "run_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    private void finishAssistantMessage(String assistantMessageId, String content, String status,
            String errorCode, String errorMessage,
            Integer promptTokens, Integer completionTokens, Double estimatedCostUsd) {
        com.imperium.astroguide.model.entity.Message m = messageService.getById(assistantMessageId);
        if (m == null) {
            return;
        }
        m.setContent(content != null ? content : "");
        m.setStatus(status);
        m.setErrorCode(errorCode);
        m.setErrorMessage(errorMessage);
        m.setPromptTokens(promptTokens);
        m.setCompletionTokens(completionTokens);
        m.setEstimatedCostUsd(estimatedCostUsd);
        messageService.updateById(m);
    }

    private static String buildSystemPrompt(String difficulty, String language) {
        String langInstruction = "zh".equalsIgnoreCase(language) ? "请使用中文回答。" : "Please answer in English.";
        String diffHint = switch (difficulty.toLowerCase()) {
            case "basic" -> "Use simple language and avoid jargon.";
            case "advanced" -> "You may use precise terminology and include formulas when helpful.";
            default -> "Explain clearly with moderate detail.";
        };
        String formatConstraints = " Output format hard constraints (must follow strictly): "
                + "(1) Keep one blank line before and after every Markdown heading. "
                + "(2) Each list item must be on its own line; never place multiple list items on the same line. "
                + "(3) Inline math must use $...$. "
                + "(4) Block math must use $$...$$ as a standalone block. "
                + "(5) Do a quick self-check before final output; if any rule is violated, rewrite to comply.";
        String markerProtocol = " For key terms or symbols, use markers: [[term:Term Name]] or [[sym:formula]]. "
                + "Optional stable key: [[term:Name|key=id]]. "
                + "Do not use [[...]] for anything other than term/sym markers.";
        return "You are a university-level astronomy tutor. " + langInstruction + " " + diffHint
                + " Structure your answer: conclusion first, then layered explanation, optional formulas, "
                + "common misconceptions, and next-step suggestions. "
                + "Use Markdown and LaTeX where appropriate. Do not fabricate citations."
                + " When helpful for factual or encyclopedic topics (e.g. definitions, historical or current public knowledge), use the search_wikipedia tool to fetch and cite information; you do not need the user to explicitly ask for Wikipedia."
                + " Answering boundary (RAG-first with general-knowledge supplement): (1) If reference materials are provided in the context, prioritize them and cite sources for the parts they cover. (2) If the user asks about multiple topics and only some are covered in the context, answer from context for those; for topics NOT in the context, do not refuse—briefly explain from your general knowledge and clearly label the boundary (e.g. in Chinese: \"资料中未提及，但一般意义上……\"; in English: \"The provided materials do not cover this; in general, …\"), then suggest consulting more resources. (3) When no reference context is provided or the context is empty, answer directly from your general astronomical knowledge; do not refuse or say the context is empty."
                + formatConstraints
                + markerProtocol;
    }

    private ServerSentEvent<String> sseError(String code, String message, String requestId) {
        Map<String, Object> errorMap = new HashMap<>(Map.of("code", code, "message", message));
        if (requestId != null) {
            errorMap.put("requestId", requestId);
        }
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
