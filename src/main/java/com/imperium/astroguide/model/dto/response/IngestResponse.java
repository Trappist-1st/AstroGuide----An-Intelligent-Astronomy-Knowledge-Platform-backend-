package com.imperium.astroguide.model.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 资料摄入接口统一响应。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "资料摄入结果")
public class IngestResponse {

    @Schema(description = "是否已接受并写入向量库")
    private boolean accepted;

    @Schema(description = "来源显示名（如书名、文件名）")
    private String source;

    @Schema(description = "本次写入的块数量")
    private int chunksAdded;

    @Schema(description = "说明或错误信息")
    private String message;
}
