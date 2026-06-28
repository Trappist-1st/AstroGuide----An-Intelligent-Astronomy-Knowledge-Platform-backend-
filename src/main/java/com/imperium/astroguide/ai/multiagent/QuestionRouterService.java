package com.imperium.astroguide.ai.multiagent;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.regex.Pattern;

/**
 * 按问题复杂度路由：默认走单 Agent 低延迟路径，复杂问题启用 Plan + Review。
 */
@Service
public class QuestionRouterService {

    private static final Pattern COMPLEX_KEYWORDS = Pattern.compile(
            "(对比|比较|分析|解释.*原理|逐步|为什么.*以及|research|compare|analyze|step.?by.?step|pros and cons|difference between)",
            Pattern.CASE_INSENSITIVE);

    private final boolean routerEnabled;
    private final int complexMinChars;
    private final int multiQuestionThreshold;

    public QuestionRouterService(
            @Value("${app.ai.router.enabled:true}") boolean routerEnabled,
            @Value("${app.ai.router.complex-min-chars:120}") int complexMinChars,
            @Value("${app.ai.router.multi-question-threshold:2}") int multiQuestionThreshold) {
        this.routerEnabled = routerEnabled;
        this.complexMinChars = complexMinChars;
        this.multiQuestionThreshold = multiQuestionThreshold;
    }

    public RouteDecision route(String userText) {
        if (!routerEnabled || userText == null || userText.isBlank()) {
            return RouteDecision.simple("router_disabled", 1.0);
        }

        String text = userText.trim();
        int questionMarks = countChar(text, '?') + countChar(text, '？');

        if (text.length() >= complexMinChars) {
            return RouteDecision.complex("long_question", 0.85);
        }
        if (questionMarks >= multiQuestionThreshold) {
            return RouteDecision.complex("multi_question", 0.9);
        }
        if (COMPLEX_KEYWORDS.matcher(text).find()) {
            return RouteDecision.complex("complex_keyword", 0.88);
        }
        if (text.chars().filter(ch -> ch == '\n').count() >= 2) {
            return RouteDecision.complex("multi_paragraph", 0.8);
        }

        return RouteDecision.simple("default_simple", 0.92);
    }

    private static int countChar(String text, char ch) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == ch) {
                count++;
            }
        }
        return count;
    }
}
