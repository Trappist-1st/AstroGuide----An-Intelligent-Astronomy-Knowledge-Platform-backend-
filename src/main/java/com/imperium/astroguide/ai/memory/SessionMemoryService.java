package com.imperium.astroguide.ai.memory;

import com.imperium.astroguide.model.entity.Message;
import com.imperium.astroguide.service.MessageService;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Session Memory：从 MySQL 加载会话历史，作为 LangGraph 工作记忆的来源。
 */
@Service
public class SessionMemoryService {

    private final MessageService messageService;
    private final int maxMessages;

    public SessionMemoryService(MessageService messageService,
            @Value("${app.ai.memory.max-messages:16}") int maxMessages) {
        this.messageService = messageService;
        this.maxMessages = maxMessages;
    }

    public List<org.springframework.ai.chat.messages.Message> loadHistory(String conversationId,
            Message currentUserMessage) {
        if (conversationId == null || conversationId.isBlank() || currentUserMessage.getCreatedAt() == null) {
            return List.of();
        }

        List<Message> history = messageService.lambdaQuery()
                .eq(Message::getConversationId, conversationId)
                .lt(Message::getCreatedAt, currentUserMessage.getCreatedAt())
                .orderByAsc(Message::getCreatedAt)
                .last("LIMIT " + maxMessages)
                .list();

        List<org.springframework.ai.chat.messages.Message> memoryMsgs = new ArrayList<>();
        for (Message m : history) {
            if (m == null || m.getContent() == null || m.getContent().isBlank()) {
                continue;
            }
            if ("assistant".equals(m.getRole())) {
                if ("queued".equalsIgnoreCase(m.getStatus())) {
                    continue;
                }
                memoryMsgs.add(new AssistantMessage(m.getContent()));
            } else {
                memoryMsgs.add(new UserMessage(m.getContent()));
            }
        }
        return memoryMsgs;
    }
}
