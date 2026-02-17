package com.imperium.astroguide.policy;

/**
 * 上下文裁剪策略：最近 N 轮 + 最大字符，对应 TDD 7、10.1。
 */
public final class ContextTrimPolicy {

    /** 默认最近对话轮数（每轮 = 1 user + 1 assistant） */
    public static final int DEFAULT_MAX_ROUNDS = 8;

    /** 上下文最大字符数（超长截断） */
    public static final int DEFAULT_MAX_CONTEXT_CHARS = 12_000;

    private ContextTrimPolicy() {}
}
