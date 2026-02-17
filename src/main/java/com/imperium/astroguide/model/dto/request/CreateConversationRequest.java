package com.imperium.astroguide.model.dto.request;

import lombok.Data;

/**
 * 创建会话请求，对应 TDD 5.1 POST /conversations。
 */
@Data
public class CreateConversationRequest {

    /** 可选标题 */
    private String title;
}
