package com.imperium.astroguide.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 会话列表响应，对应 TDD 5.1 GET /conversations。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationListResponse {

    private List<ConversationListItemDto> items;
    /** 游标分页下一页；null 表示没有更多 */
    private String nextCursor;
}
