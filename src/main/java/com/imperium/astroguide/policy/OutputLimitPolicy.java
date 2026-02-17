package com.imperium.astroguide.policy;

/**
 * 最大输出 token 限制，按难度档位，对应 TDD 7、10.1。
 */
public final class OutputLimitPolicy {

    public static final int MAX_TOKENS_BASIC = 1_500;
    public static final int MAX_TOKENS_INTERMEDIATE = 2_000;
    public static final int MAX_TOKENS_ADVANCED = 2_500;

    /**
     * 按难度返回允许的最大 completion tokens。
     *
     * @param difficulty basic | intermediate | advanced
     * @return max tokens
     */
    public static int getMaxCompletionTokens(String difficulty) {
        if (difficulty == null || difficulty.isBlank()) {
            return MAX_TOKENS_INTERMEDIATE;
        }
        return switch (difficulty.toLowerCase()) {
            case "basic" -> MAX_TOKENS_BASIC;
            case "advanced" -> MAX_TOKENS_ADVANCED;
            default -> MAX_TOKENS_INTERMEDIATE;
        };
    }

    private OutputLimitPolicy() {}
}
