package com.imperium.astroguide.model.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 直接提交一段文本用于摄入向量库。
 */
@Data
@Schema(description = "文本摄入请求")
public class IngestTextRequest {

    @NotBlank(message = "content is required")
    @Size(min = 1, max = 500_000)
    @Schema(description = "要摄入的正文", requiredMode = Schema.RequiredMode.REQUIRED)
    private String content;

    @Size(max = 500)
    @Schema(description = "来源显示名（如《基础天文学》第3章）")
    private String sourceName;
}
