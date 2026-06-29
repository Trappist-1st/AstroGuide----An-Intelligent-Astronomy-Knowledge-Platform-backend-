package com.imperium.astroguide.ai.memory;

import com.imperium.astroguide.infra.coordination.DistributedLock;
import com.imperium.astroguide.mapper.ConversationMemorySummaryMapper;
import com.imperium.astroguide.model.entity.ConversationMemorySummary;
import com.imperium.astroguide.model.entity.Message;
import com.imperium.astroguide.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 异步 Summary Memory Worker：对话结束后压缩早期轮次，不阻塞 SSE 主链路。
 * <p>
 * 多实例下通过 {@link DistributedLock} 保证同一会话仅一个实例执行摘要。
 */
@Service
public class SummaryMemoryWorker {

    private static final Logger log = LoggerFactory.getLogger(SummaryMemoryWorker.class);
    private static final String LOCK_PREFIX = "summary:";

    private static final String SUMMARY_PROMPT = """
            Summarize the following astronomy tutoring conversation in 3-6 sentences.
            Preserve key topics, user goals, and important conclusions. Use the same language as the dialogue.
            Conversation:
            %s
            """;

    private final MessageService messageService;
    private final ConversationMemorySummaryMapper summaryMapper;
    private final ChatClient chatClient;
    private final DistributedLock distributedLock;
    private final int minMessagesToSummarize;
    private final int maxMessagesInWindow;
    private final long lockTtlMs;

    public SummaryMemoryWorker(MessageService messageService,
            ConversationMemorySummaryMapper summaryMapper,
            ChatClient chatClient,
            DistributedLock distributedLock,
            @Value("${app.ai.memory.summary-min-messages:6}") int minMessagesToSummarize,
            @Value("${app.ai.memory.summary-window:24}") int maxMessagesInWindow,
            @Value("${app.ai.memory.summary-lock-ttl-ms:120000}") long lockTtlMs) {
        this.messageService = messageService;
        this.summaryMapper = summaryMapper;
        this.chatClient = chatClient;
        this.distributedLock = distributedLock;
        this.minMessagesToSummarize = minMessagesToSummarize;
        this.maxMessagesInWindow = maxMessagesInWindow;
        this.lockTtlMs = lockTtlMs;
    }

    @Async("memoryTaskExecutor")
    public void summarizeConversation(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return;
        }
        String lockKey = LOCK_PREFIX + conversationId;
        if (!distributedLock.tryLock(lockKey, lockTtlMs)) {
            log.debug("skip summary, lock held conversationId={}", conversationId);
            return;
        }
        try {
            doSummarize(conversationId);
        } finally {
            distributedLock.unlock(lockKey);
        }
    }

    private void doSummarize(String conversationId) {
        List<Message> messages = messageService.lambdaQuery()
                .eq(Message::getConversationId, conversationId)
                .in(Message::getRole, "user", "assistant")
                .eq(Message::getStatus, "done")
                .orderByAsc(Message::getCreatedAt)
                .last("LIMIT " + maxMessagesInWindow)
                .list();

        if (messages.size() < minMessagesToSummarize) {
            log.debug("skip summary, not enough messages conversationId={} count={}", conversationId, messages.size());
            return;
        }

        String transcript = messages.stream()
                .filter(m -> m.getContent() != null && !m.getContent().isBlank())
                .map(m -> m.getRole() + ": " + truncate(m.getContent(), 800))
                .collect(Collectors.joining("\n"));

        if (transcript.isBlank()) {
            return;
        }

        String summary;
        try {
            summary = chatClient.prompt()
                    .user(SUMMARY_PROMPT.formatted(transcript))
                    .call()
                    .content();
        } catch (Exception e) {
            log.warn("summary LLM failed conversationId={}: {}", conversationId, e.getMessage());
            return;
        }

        if (summary == null || summary.isBlank()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        ConversationMemorySummary existing = summaryMapper.selectById(conversationId);
        int version = existing != null && existing.getVersion() != null ? existing.getVersion() + 1 : 1;

        ConversationMemorySummary row = new ConversationMemorySummary();
        row.setConversationId(conversationId);
        row.setSummary(summary.trim());
        row.setMessageCount(messages.size());
        row.setVersion(version);
        row.setUpdatedAt(now);
        if (existing == null) {
            row.setCreatedAt(now);
            summaryMapper.insert(row);
        } else {
            summaryMapper.updateById(row);
        }

        log.info("summary memory updated conversationId={} version={} messageCount={}",
                conversationId, version, messages.size());
    }

    private static String truncate(String text, int maxLen) {
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
