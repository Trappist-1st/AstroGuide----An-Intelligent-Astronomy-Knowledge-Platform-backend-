package com.imperium.astroguide.ai.runtime;

import com.imperium.astroguide.ai.graph.AstroGuideWorkflowRunner;
import com.imperium.astroguide.ai.graph.AstroGuideWorkflowRunner.WorkflowExecution;
import com.imperium.astroguide.service.AgentRunService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.UUID;

/**
 * LangGraph Agent Runtime：委托 {@link AstroGuideWorkflowRunner} 执行 Phase 3 工作流。
 */
@Service
public class LangGraphAgentRunner implements AgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(LangGraphAgentRunner.class);

    private final AstroGuideWorkflowRunner workflowRunner;
    private final AgentRunService agentRunService;
    private final AgentRunCancellationRegistry cancellationRegistry;

    @Value("${spring.ai.openai.chat.options.model:deepseek-chat}")
    private String defaultModel;

    public LangGraphAgentRunner(AstroGuideWorkflowRunner workflowRunner,
            AgentRunService agentRunService,
            AgentRunCancellationRegistry cancellationRegistry) {
        this.workflowRunner = workflowRunner;
        this.agentRunService = agentRunService;
        this.cancellationRegistry = cancellationRegistry;
    }

    @Override
    public Flux<AgentStreamEvent> stream(AgentRunRequest request) {
        return Flux.create(sink -> Schedulers.boundedElastic().schedule(() -> run(request, sink)));
    }

    private void run(AgentRunRequest request, FluxSink<AgentStreamEvent> sink) {
        long startMs = System.currentTimeMillis();
        String runId = request.runId() != null ? request.runId() : newRunId();
        cancellationRegistry.register(runId);

        try {
            agentRunService.markRunning(runId, request.requestId(), request.conversationId(),
                    request.messageId(), defaultModel);

            WorkflowExecution execution = workflowRunner.execute(
                    new AgentRunRequest(
                            runId,
                            request.requestId(),
                            request.conversationId(),
                            request.messageId(),
                            request.systemPrompt(),
                            request.userText(),
                            request.historyMessages(),
                            request.maxCompletionTokens(),
                            request.ragCitations(),
                            request.conversationSummary()),
                    sink::next);

            AgentRunResult result = new AgentRunResult(
                    runId,
                    execution.finalText(),
                    execution.promptTokens(),
                    execution.completionTokens(),
                    execution.citations(),
                    execution.toolExecutions(),
                    execution.nodeTimingsMs(),
                    "completed",
                    execution.estimatedInputTokens(),
                    execution.routeDecision().mode().name(),
                    execution.routeDecision().reasonCode(),
                    execution.reviewResult().passed(),
                    execution.reviewResult().reasonCode());

            int latencyMs = (int) (System.currentTimeMillis() - startMs);
            agentRunService.markFinished(result, latencyMs, "completed", null);
            sink.next(new AgentStreamEvent.RunFinished(result));
            sink.complete();
        } catch (AgentRunCancelledException e) {
            int latencyMs = (int) (System.currentTimeMillis() - startMs);
            AgentRunResult cancelled = new AgentRunResult(
                    runId, "", null, null, List.of(), List.of(), java.util.Map.of(), "cancelled", null);
            agentRunService.markFinished(cancelled, latencyMs, "cancelled", null);
            log.info("agent run cancelled runId={} conversationId={} messageId={}",
                    runId, request.conversationId(), request.messageId());
            sink.complete();
        } catch (Exception e) {
            int latencyMs = (int) (System.currentTimeMillis() - startMs);
            AgentRunResult failed = new AgentRunResult(
                    runId, "", null, null, List.of(), List.of(), java.util.Map.of(), "failed", null);
            agentRunService.markFinished(failed, latencyMs, "failed", e.getMessage());
            log.warn("agent run failed runId={} conversationId={} messageId={} error={}",
                    runId, request.conversationId(), request.messageId(), e.getMessage(), e);
            sink.error(e);
        } finally {
            cancellationRegistry.unregister(runId);
        }
    }

    private static String newRunId() {
        return "run_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }
}
