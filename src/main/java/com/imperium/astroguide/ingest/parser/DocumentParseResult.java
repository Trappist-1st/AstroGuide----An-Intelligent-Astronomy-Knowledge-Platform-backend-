package com.imperium.astroguide.ingest.parser;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 解析结果：整份资料的纯文本及可选来源标签（如书名）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentParseResult {

    /** 解析出的完整正文（已拼接） */
    private String fullText;

    /** 来源显示名（如书名、文件名），用于 metadata.source */
    private String sourceLabel;
}
