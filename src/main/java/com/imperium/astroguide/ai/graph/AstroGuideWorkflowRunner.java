package com.imperium.astroguide.ai.graph;

import com.imperium.astroguide.ai.context.ContextAssemblyService;
import com.imperium.astroguide.ai.context.ContextAssemblyService.PreparedContext;
import com.imperium.astroguide.ai.multiagent.AnswerReviewerService;
import com.imperium.astroguide.ai.multiagent.PlannerService;
import com.imperium.astroguide.ai.multiagent.QuestionRouterService;
import com.imperium.astroguide.ai.multiagent.ReviewResult;
import com.imperium.astroguide.ai.multiagent.RouteDecision;
import com.imperium.astroguide.ai.multiagent.RouteMode;
import com.imperium.astroguide.ai.rag.RagRetrievalResult;
import com.imperium.astroguide.ai.rag.RagRetrievalService;
import com.imperium.astroguide.ai.runtime.AgentRunContext;
import com.imperium.astroguide.ai.runtime.AgentRunRequest;
import com.imperium.astroguide.ai.runtime.AgentStreamEvent;
import com.imperium.astroguide.ai.tool.ChatRunContext;
import com.imperium.astroguide.ai.tool.ToolExecutionRecord;
import com.imperium.astroguide.ai.tool.ToolPolicyService;
import com.imperium.astroguide.model.dto.rag.CitationDto;
import org.bsc.langgraph4j.CompiledGraph;
import org.bsc.langgraph4j.GraphInput;
import org.bsc.langgraph4j.RunnableConfig;
import org.bsc.langgraph4j.spring.ai.agentexecutor.AgentExecutor;
import org.bsc.langgraph4j.streaming.StreamingOutput;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Phase 3 Workflow：route → retrieve → [plan] → prepare → react → review → finalize。
 */
@Service
public class AstroGuideWorkflowRunner {

    public static final String NODE_ROUTE = "route";
    public static final String NODE_PLAN = "plan";
    public static final String NODE_PREPARE = "prepare_context";
    public static final String NODE_RETRIEVE = "retrieve_knowledge";
    public static final String NODE_REACT = "react_agent";
    public static final String NODE_REVIEW = "review_answer";
    public static final String NODE_FINALIZE = "finalize";

    private static final Logger log = LoggerFactory.getLogger(AstroGuideWorkflowRunner.class);

    private final ContextAssemblyService contextAssemblyService;
    private final RagRetrievalService ragRetrievalService;
    private final ToolPolicyService toolPolicyService;
    private final QuestionRouterService questionRouterService;
    private final PlannerService plannerService;
    private final AnswerReviewerService answerReviewerService;
    private final CompiledGraph<AgentExecutor.State> reactGraph;

    public AstroGuideWorkflowRunner(ContextAssemblyService contextAssemblyService,
            RagRetrievalService ragRetrievalService,
            ToolPolicyService toolPolicyService,
            QuestionRouterService questionRouterService,
            PlannerService plannerService,
            AnswerReviewerService answerReviewerService,
            CompiledGraph<AgentExecutor.State> reactGraph) {
        this.contextAssemblyService = contextAssemblyService;
        this.ragRetrievalService = ragRetrievalService;
        this.toolPolicyService = toolPolicyService;
        this.questionRouterService = questionRouterService;
        this.plannerService = plannerService;
        this.answerReviewerService = answerReviewerService;
        this.reactGraph = reactGraph;
    }

    public WorkflowExecution execute(AgentRunRequest request, Consumer<AgentStreamEvent> eventConsumer) throws Exception {
        AgentRunContext metrics = new AgentRunContext();
        AtomicReference<Integer> promptTokens = new AtomicReference<>();
        AtomicReference<Integer> completionTokens = new AtomicReference<>();
        AtomicReference<AgentExecutor.State> lastState = new AtomicReference<>();

        return ChatRunContext.run(request.runId(), request.maxCompletionTokens(), toolPolicyService, eventConsumer, () -> {
            RouteDecision route = runPhase(metrics, eventConsumer, NODE_ROUTE, () -> {
                RouteDecision decision = questionRouterService.route(request.userText());
                eventConsumer.accept(new AgentStreamEvent.RouteSelected(
                        decision.mode().name(),
                        decision.reasonCode(),
                        decision.confidence()));
                return decision;
            });

            RagRetrievalResult rag = runPhase(metrics, eventConsumer, NODE_RETRIEVE,
                    () -> ragRetrievalService.retrieve(request.userText()));

            String executionPlan = "";
            if (route.mode() == RouteMode.COMPLEX) {
                executionPlan = runPhase(metrics, eventConsumer, NODE_PLAN,
                        () -> plannerService.plan(request.userText()));
            }
            final String planForContext = executionPlan;

            String summary = request.conversationSummary() != null ? request.conversationSummary() : "";
            PreparedContext prepared = runPhase(metrics, eventConsumer, NODE_PREPARE, () ->
                    contextAssemblyService.prepare(
                            request.systemPrompt(),
                            request.historyMessages(),
                            request.userText(),
                            rag,
                            summary,
                            planForContext));

            runPhase(metrics, eventConsumer, NODE_REACT, () -> {
                Map<String, Object> input = Map.of("messages", prepared.messages());
                RunnableConfig config = RunnableConfig.builder()
                        .threadId(checkpointThreadId(request))
                        .build();
                var generator = reactGraph.stream(GraphInput.args(input), config);
                AtomicReference<String> activeNode = new AtomicReference<>();

                generator.stream().forEach(output -> {
                    String nodeName = output.node() != null ? output.node() : NODE_REACT;
                    trackReactNode(metrics, activeNode, nodeName, eventConsumer);

                    if (output instanceof StreamingOutput streamingOutput) {
                        captureUsage(streamingOutput, promptTokens, completionTokens);
                        captureState(lastState, streamingOutput.state());
                        String chunk = streamingOutput.chunk();
                        if (chunk != null && !chunk.isBlank()) {
                            eventConsumer.accept(new AgentStreamEvent.TextDelta(chunk));
                        }
                        return;
                    }
                    captureState(lastState, output.state());
                });

                closeActiveNode(metrics, activeNode, eventConsumer);
                return null;
            });

            String draftText = extractFinalText(lastState.get());
            ReviewResult review = runPhase(metrics, eventConsumer, NODE_REVIEW, () -> {
                ReviewResult result = answerReviewerService.review(
                        request.userText(),
                        draftText,
                        route.mode(),
                        rag.hasContext());
                String override = !Objects.equals(result.finalText(), draftText) ? result.finalText() : null;
                eventConsumer.accept(new AgentStreamEvent.ReviewCompleted(
                        result.passed(), result.reasonCode(), override));
                return result;
            });

            return runPhase(metrics, eventConsumer, NODE_FINALIZE, () -> {
                AgentExecutor.State finalState = lastState.get();
                List<ToolExecutionRecord> toolExecutions = extractToolExecutions(finalState);
                List<CitationDto> citations = mergeCitations(request.ragCitations(), rag.citations());

                WorkflowExecution execution = new WorkflowExecution(
                        review.finalText(),
                        promptTokens.get(),
                        completionTokens.get(),
                        citations,
                        toolExecutions,
                        metrics.nodeTimingsMs(),
                        prepared.estimatedInputTokens(),
                        route,
                        review);

                log.info("workflow completed runId={} route={} review={} estimatedInputTokens={} toolCalls={}",
                        request.runId(), route.mode(), review.reasonCode(),
                        execution.estimatedInputTokens(), execution.toolExecutions().size());
                return execution;
            });
        });
    }

    private static String checkpointThreadId(AgentRunRequest request) {
        return request.conversationId() + ":" + request.runId();
    }

    private <T> T runPhase(AgentRunContext metrics,
            Consumer<AgentStreamEvent> eventConsumer,
            String nodeId,
            PhaseAction<T> action) throws Exception {
        long startMs = System.currentTimeMillis();
        metrics.markNodeStart(nodeId);
        eventConsumer.accept(new AgentStreamEvent.NodeStarted(nodeId));
        try {
            return action.run();
        } finally {
            metrics.markNodeEnd(nodeId);
            eventConsumer.accept(new AgentStreamEvent.NodeFinished(nodeId, System.currentTimeMillis() - startMs));
        }
    }

    private void trackReactNode(AgentRunContext metrics,
            AtomicReference<String> activeNode,
            String nodeId,
            Consumer<AgentStreamEvent> eventConsumer) {
        String previous = activeNode.get();
        if (Objects.equals(previous, nodeId)) {
            return;
        }
        if (previous != null) {
            metrics.markNodeEnd(previous);
            Long latency = metrics.nodeTimingsMs().get(previous);
            eventConsumer.accept(new AgentStreamEvent.NodeFinished(previous, latency != null ? latency : 0L));
        }
        metrics.markNodeStart(nodeId);
        activeNode.set(nodeId);
        eventConsumer.accept(new AgentStreamEvent.NodeStarted(nodeId));
    }

    private void closeActiveNode(AgentRunContext metrics,
            AtomicReference<String> activeNode,
            Consumer<AgentStreamEvent> eventConsumer) {
        String nodeId = activeNode.get();
        if (nodeId == null) {
            return;
        }
        metrics.markNodeEnd(nodeId);
        Long latency = metrics.nodeTimingsMs().get(nodeId);
        eventConsumer.accept(new AgentStreamEvent.NodeFinished(nodeId, latency != null ? latency : 0L));
        activeNode.set(null);
    }

    private static String extractFinalText(AgentExecutor.State state) {
        if (state == null) {
            return "";
        }
        return state.messages().stream()
                .filter(AssistantMessage.class::isInstance)
                .map(AssistantMessage.class::cast)
                .reduce((first, second) -> second)
                .map(AssistantMessage::getText)
                .orElse("");
    }

    private static List<ToolExecutionRecord> extractToolExecutions(AgentExecutor.State state) {
        Map<String, ToolExecutionRecord> records = new LinkedHashMap<>();
        if (state == null) {
            return List.of();
        }
        for (Message message : state.messages()) {
            if (!(message instanceof ToolResponseMessage toolResponseMessage)) {
                continue;
            }
            toolResponseMessage.getResponses().forEach(response -> records.putIfAbsent(response.name(),
                    ToolExecutionRecord.builder()
                            .toolName(response.name())
                            .arguments("")
                            .success(true)
                            .latencyMs(0L)
                            .resultPreview(truncate(response.responseData(), 300))
                            .build()));
        }
        return new ArrayList<>(records.values());
    }

    private static List<CitationDto> mergeCitations(List<CitationDto> fromRequest, List<CitationDto> fromRag) {
        if (fromRequest != null && !fromRequest.isEmpty()) {
            return fromRequest;
        }
        return fromRag != null ? fromRag : List.of();
    }

    private static void captureState(AtomicReference<AgentExecutor.State> lastState,
            org.bsc.langgraph4j.state.AgentState state) {
        if (state instanceof AgentExecutor.State agentState) {
            lastState.set(agentState);
        }
    }

    private static void captureUsage(StreamingOutput output,
            AtomicReference<Integer> promptTokens,
            AtomicReference<Integer> completionTokens) {
        output.metadata("chatResponse").ifPresent(value -> {
            if (value instanceof ChatResponse chatResponse) {
                applyUsage(chatResponse, promptTokens, completionTokens);
            }
        });
    }

    private static void applyUsage(ChatResponse chatResponse,
            AtomicReference<Integer> promptTokens,
            AtomicReference<Integer> completionTokens) {
        if (chatResponse.getMetadata() == null || chatResponse.getMetadata().getUsage() == null) {
            return;
        }
        Usage usage = chatResponse.getMetadata().getUsage();
        if (usage.getPromptTokens() != null && usage.getPromptTokens() > 0) {
            promptTokens.set(usage.getPromptTokens());
        }
        if (usage.getCompletionTokens() != null && usage.getCompletionTokens() > 0) {
            completionTokens.set(usage.getCompletionTokens());
        }
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) {
            return "";
        }
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

    @FunctionalInterface
    private interface PhaseAction<T> {
        T run() throws Exception;
    }

    public record WorkflowExecution(
            String finalText,
            Integer promptTokens,
            Integer completionTokens,
            List<CitationDto> citations,
            List<ToolExecutionRecord> toolExecutions,
            Map<String, Long> nodeTimingsMs,
            int estimatedInputTokens,
            RouteDecision routeDecision,
            ReviewResult reviewResult) {
    }
}
