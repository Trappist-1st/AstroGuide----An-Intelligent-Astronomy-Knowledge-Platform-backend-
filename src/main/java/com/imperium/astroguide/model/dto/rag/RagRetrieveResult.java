package com.imperium.astroguide.model.dto.rag;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * V1 RAG：检索结果，包含拼入 prompt 的参考文本与 citations 列表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RagRetrieveResult {

    /** 拼入 [参考] 区的文本（RAG 或 Wikipedia 片段拼接） */
    private String referenceText;
    /** 参考来源列表，用于 done.citations */
    @Builder.Default
    private List<CitationDto> citations = new ArrayList<>();

    public static RagRetrieveResult empty() {
        return RagRetrieveResult.builder()
                .referenceText("")
                .citations(new ArrayList<>())
                .build();
    }
}
