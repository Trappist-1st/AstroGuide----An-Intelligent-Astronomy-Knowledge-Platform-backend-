package com.imperium.astroguide.model.dto.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * V1 RAG：单条参考来源，用于 SSE done 事件的 citations 数组。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CitationDto {

    /** 片段唯一 ID（如 chunk_id 或 "wiki_标题_0"） */
    private String chunkId;
    /** 可读来源（如 "Wikipedia: Type Ia supernova"、"《书名》: 第12章"） */
    private String source;
    /** 摘要或片段原文 */
    private String excerpt;
}
