package com.imperium.astroguide.ai.context;

import com.imperium.astroguide.ai.rag.RagRetrievalResult;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Context Engineering：组装 System + Summary + Plan + 裁剪历史 + RAG 注入 + 当前用户问题。
 */
@Service
public class ContextAssemblyService {

    private static final String RAG_USER_TEMPLATE = """
            User question: %s

            Reference context (use where relevant; cite sources for these parts):
            ---------------------
            %s
            ---------------------

            Instructions: Answer fully. For parts covered by the reference context, use it and cite. \
            For parts NOT covered, answer from general knowledge and clearly label the boundary.
            """;

    private static final String SUMMARY_SYSTEM_SUFFIX = """

            Conversation summary (earlier context, use if relevant):
            ---------------------
            %s
            ---------------------
            """;

    private static final String PLAN_SYSTEM_SUFFIX = """

            Execution plan (follow this structure for a thorough answer):
            ---------------------
            %s
            ---------------------
            """;

    private final ContextTrimPipeline contextTrimPipeline;
    private final TokenBudgetEstimator tokenBudgetEstimator;

    public ContextAssemblyService(ContextTrimPipeline contextTrimPipeline,
            TokenBudgetEstimator tokenBudgetEstimator) {
        this.contextTrimPipeline = contextTrimPipeline;
        this.tokenBudgetEstimator = tokenBudgetEstimator;
    }

    public PreparedContext prepare(String systemPrompt,
            List<Message> historyMessages,
            String userText,
            RagRetrievalResult ragResult) {
        return prepare(systemPrompt, historyMessages, userText, ragResult, "", "");
    }

    public PreparedContext prepare(String systemPrompt,
            List<Message> historyMessages,
            String userText,
            RagRetrievalResult ragResult,
            String conversationSummary,
            String executionPlan) {
        List<Message> trimmedHistory = contextTrimPipeline.trimHistory(historyMessages);
        List<Message> messages = new ArrayList<>();

        String enrichedSystem = enrichSystemPrompt(systemPrompt, conversationSummary, executionPlan);
        messages.add(new SystemMessage(enrichedSystem));
        messages.addAll(trimmedHistory);

        String userContent = userText != null ? userText : "";
        if (ragResult != null && ragResult.hasContext()) {
            userContent = RAG_USER_TEMPLATE.formatted(userContent, ragResult.referenceContext());
        }
        messages.add(new UserMessage(userContent));

        int estimatedInputTokens = tokenBudgetEstimator.estimateTokens(messages);
        return new PreparedContext(messages, trimmedHistory.size(), estimatedInputTokens);
    }

    private static String enrichSystemPrompt(String systemPrompt,
            String conversationSummary,
            String executionPlan) {
        StringBuilder sb = new StringBuilder(systemPrompt != null ? systemPrompt : "");
        if (conversationSummary != null && !conversationSummary.isBlank()) {
            sb.append(SUMMARY_SYSTEM_SUFFIX.formatted(conversationSummary.trim()));
        }
        if (executionPlan != null && !executionPlan.isBlank()) {
            sb.append(PLAN_SYSTEM_SUFFIX.formatted(executionPlan.trim()));
        }
        return sb.toString();
    }

    public record PreparedContext(
            List<Message> messages,
            int historyMessageCount,
            int estimatedInputTokens) {
    }
}
