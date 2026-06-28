package com.imperium.astroguide.ai.context;

/**
 * 上下文裁剪策略：控制多轮历史与总字符上限，防止 Context Window 爆炸。
 */
public final class ContextTrimPolicy {

    /** 默认保留最近对话轮数（1 轮 = 1 user + 1 assistant） */
    public static final int DEFAULT_MAX_ROUNDS = 8;

    /** 历史消息最大字符数（粗粒度 token 控制） */
    public static final int DEFAULT_MAX_HISTORY_CHARS = 12_000;

    /** 单条 tool 返回最大字符数 */
    public static final int DEFAULT_MAX_TOOL_RESULT_CHARS = 1_500;

    /** 单个 RAG 片段最大字符数 */
    public static final int DEFAULT_MAX_RAG_EXCERPT_CHARS = 500;

    private ContextTrimPolicy() {
    }
}
