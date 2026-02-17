package com.imperium.astroguide.model.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 会话详情中的单条消息，对应 TDD 5.1 GET /conversations/{id} 的 messages 元素。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageInConversationDto {

    private String id;
    private String role;
    private String content;
    private String difficulty;
    private String language;
    private String status;

    private Integer promptTokens;
    private Integer completionTokens;
    /** 估算费用（美元），V0 可无此字段或为 null */
    private Double estimatedCostUsd;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private LocalDateTime createdAt;
}
