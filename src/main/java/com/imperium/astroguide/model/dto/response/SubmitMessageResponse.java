package com.imperium.astroguide.model.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 提交用户消息响应 (202)，对应 TDD 5.2。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitMessageResponse {

    /** 用户消息 ID，用于后续请求 stream */
    private String messageId;

    /** 流式回答地址，GET 此 URL 获取 assistant 回复 */
    private String streamUrl;

    /** 状态：queued（assistant 回复待生成） */
    private String status;
}
