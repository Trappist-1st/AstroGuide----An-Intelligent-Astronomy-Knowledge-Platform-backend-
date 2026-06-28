package com.imperium.astroguide.ai.multiagent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 回答质量 Reviewer：规则校验为主，复杂路径可选 LLM 轻量修订。
 */
@Service
public class AnswerReviewerService {

    private static final Logger log = LoggerFactory.getLogger(AnswerReviewerService.class);

    private static final String REVIEWER_PROMPT = """
            You are a strict astronomy tutor reviewer. Check the draft answer for:
            1) non-empty substantive content, 2) clear structure, 3) no fabricated citations.
            If acceptable, reply exactly: APPROVED
            If needs minor fix, reply: REVISED
            then the improved answer on following lines (keep Markdown/LaTeX).
            User question: %s
            Draft answer:
            %s
            """;

    private final ChatClient chatClient;
    private final boolean enabled;
    private final boolean llmReviewForComplex;
    private final int minAnswerChars;

    public AnswerReviewerService(ChatClient chatClient,
            @Value("${app.ai.reviewer.enabled:true}") boolean enabled,
            @Value("${app.ai.reviewer.llm-for-complex:true}") boolean llmReviewForComplex,
            @Value("${app.ai.reviewer.min-answer-chars:40}") int minAnswerChars) {
        this.chatClient = chatClient;
        this.enabled = enabled;
        this.llmReviewForComplex = llmReviewForComplex;
        this.minAnswerChars = minAnswerChars;
    }

    public ReviewResult review(String userText,
            String draftAnswer,
            RouteMode routeMode,
            boolean ragUsed) {
        if (!enabled) {
            return ReviewResult.pass(draftAnswer != null ? draftAnswer : "");
        }

        String answer = draftAnswer != null ? draftAnswer.trim() : "";
        List<String> notes = new ArrayList<>();

        if (answer.isBlank()) {
            return ReviewResult.revised(
                    "I could not produce a complete answer. Please try rephrasing your astronomy question.",
                    "empty_answer",
                    List.of("draft was empty"));
        }
        if (answer.length() < minAnswerChars) {
            notes.add("answer shorter than minimum threshold");
        }
        if (ragUsed && !containsBoundaryHint(answer)) {
            notes.add("rag context provided but answer may lack source boundary language");
        }

        if (routeMode == RouteMode.COMPLEX && llmReviewForComplex) {
            return llmReview(userText, answer, notes);
        }

        return new ReviewResult(true, answer, notes.isEmpty() ? "passed" : "passed_with_notes", notes);
    }

    private ReviewResult llmReview(String userText, String answer, List<String> notes) {
        try {
            String response = chatClient.prompt()
                    .user(REVIEWER_PROMPT.formatted(userText, answer))
                    .call()
                    .content();
            if (response == null || response.isBlank()) {
                return new ReviewResult(true, answer, "llm_review_skipped", notes);
            }
            String trimmed = response.trim();
            if (trimmed.startsWith("APPROVED")) {
                return new ReviewResult(true, answer, "llm_approved", notes);
            }
            if (trimmed.startsWith("REVISED")) {
                String revised = trimmed.substring("REVISED".length()).trim();
                if (revised.startsWith(":")) {
                    revised = revised.substring(1).trim();
                }
                if (!revised.isBlank()) {
                    notes.add("llm_revised");
                    return ReviewResult.revised(revised, "llm_revised", notes);
                }
            }
            return new ReviewResult(true, answer, "llm_unclear", notes);
        } catch (Exception e) {
            log.warn("reviewer llm failed, keep draft: {}", e.getMessage());
            return new ReviewResult(true, answer, "llm_review_failed", notes);
        }
    }

    private static boolean containsBoundaryHint(String answer) {
        String lower = answer.toLowerCase();
        return lower.contains("reference") || lower.contains("context")
                || lower.contains("资料") || lower.contains("引用")
                || lower.contains("generally") || lower.contains("一般");
    }
}
