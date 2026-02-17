package com.imperium.astroguide.model.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Concept Card 返回结构，对应 TDD 4.3。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConceptCardResponse {

    /** 概念标识（如 chandra_limit） */
    private String key;

    /** 展示标题 */
    private String title;

    /** 1～3 句简短定义（JSON 字段名为 short） */
    @JsonProperty("short")
    private String shortDescription;

    /** 结构化详情：label + value */
    private List<DetailItem> details;

    /** 相关概念/术语 */
    private List<String> seeAlso;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DetailItem {
        private String label;
        private String value;
    }
}
