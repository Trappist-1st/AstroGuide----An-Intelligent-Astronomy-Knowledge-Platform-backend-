package com.imperium.astroguide.model.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 提交用户消息请求，对应 TDD 5.2 POST /conversations/{conversationId}/messages。
 */
@Data
public class SubmitMessageRequest {

    /** 必填，1~4000 字符 */
    @NotBlank(message = "content is required")
    @Size(min = 1, max = 4000, message = "content length must be 1~4000")
    private String content;

    /** 难度：basic | intermediate | advanced（可选） */
    @Pattern(regexp = "^(basic|intermediate|advanced)?$", message = "difficulty must be one of: basic, intermediate, advanced")
    private String difficulty;

    /** 语言：en | zh（可选） */
    @Pattern(regexp = "^(en|zh)?$", message = "language must be one of: en, zh")
    private String language;

    /** 可选，前端幂等键，同一会话下相同 clientMessageId 返回同一 messageId */
    private String clientMessageId;
}
