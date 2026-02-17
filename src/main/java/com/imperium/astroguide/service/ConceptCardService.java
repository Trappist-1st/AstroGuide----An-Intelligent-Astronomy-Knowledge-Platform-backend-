package com.imperium.astroguide.service;

import com.imperium.astroguide.model.dto.response.ConceptCardResponse;

/**
 * Concept Card 查询与生成：按 (type, lang, key/text) 查缓存，未命中可生成并缓存。
 */
public interface ConceptCardService {

    /**
     * 查询概念卡片。优先按 (type, lang, key) 查缓存；key 为空时可用 text 推导缓存键或按 title 查。
     * 未命中且开启生成时尝试用 LLM 生成并缓存。
     *
     * @param type term | sym
     * @param lang en | zh
     * @param key  可选，回答中的 key（如 chandra_limit）
     * @param text 可选，展示文本，用于无 key 时的查找或生成输入
     * @return 卡片内容，未命中且不生成时返回 null
     */
    ConceptCardResponse lookup(String type, String lang, String key, String text);
}
