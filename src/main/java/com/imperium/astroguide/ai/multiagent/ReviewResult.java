package com.imperium.astroguide.ai.multiagent;

import java.util.List;

/**
 * Reviewer 节点输出：规则校验 + 可选 LLM 修订。
 */
public record ReviewResult(
        boolean passed,
        String finalText,
        String reasonCode,
        List<String> notes) {

    public static ReviewResult pass(String text) {
        return new ReviewResult(true, text, "passed", List.of());
    }

    public static ReviewResult revised(String text, String reasonCode, List<String> notes) {
        return new ReviewResult(false, text, reasonCode, notes);
    }
}
