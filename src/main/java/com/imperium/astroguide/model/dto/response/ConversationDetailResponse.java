package com.imperium.astroguide.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 会话详情 + 最近消息，对应 TDD 5.1 GET /conversations/{conversationId}。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationDetailResponse {

    private ConversationResponse conversation;
    private List<MessageInConversationDto> messages;
    /** 向前翻页游标（更早的消息）；null 表示没有更早 */
    private String nextBefore;
}
