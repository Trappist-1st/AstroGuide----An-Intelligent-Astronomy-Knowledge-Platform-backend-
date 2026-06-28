package com.imperium.astroguide.ai.memory;

import com.imperium.astroguide.mapper.ConversationMemorySummaryMapper;
import com.imperium.astroguide.model.entity.ConversationMemorySummary;
import org.springframework.stereotype.Service;

/**
 * Summary Memory 读取：供 Context Assembly 注入早期对话摘要。
 */
@Service
public class SummaryMemoryService {

    private final ConversationMemorySummaryMapper summaryMapper;

    public SummaryMemoryService(ConversationMemorySummaryMapper summaryMapper) {
        this.summaryMapper = summaryMapper;
    }

    public String loadSummary(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return "";
        }
        ConversationMemorySummary row = summaryMapper.selectById(conversationId);
        if (row == null || row.getSummary() == null || row.getSummary().isBlank()) {
            return "";
        }
        return row.getSummary().trim();
    }
}
