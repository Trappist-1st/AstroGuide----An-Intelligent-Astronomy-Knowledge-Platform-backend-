package com.imperium.astroguide.service;

import com.imperium.astroguide.model.dto.rag.RagRetrieveResult;

/**
 * V1 Wikipedia 按需：根据用户问题调用 MediaWiki REST API，返回 1–2 条摘要与 citations。
 */
public interface WikipediaService {

    /**
     * 根据问题搜索 Wikipedia，取最多 max-results 条摘要，拼入参考区并生成 citations。
     *
     * @param query 用户当前问题（或从中抽取的关键词）
     * @return 参考文本 + citations（source 如 "Wikipedia: 词条名"）
     */
    RagRetrieveResult fetchForQuery(String query);
}
