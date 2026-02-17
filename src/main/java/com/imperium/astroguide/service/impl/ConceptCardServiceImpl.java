package com.imperium.astroguide.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.imperium.astroguide.model.dto.response.ConceptCardResponse;
import com.imperium.astroguide.model.entity.TermCache;
import com.imperium.astroguide.service.ConceptCardService;
import com.imperium.astroguide.service.TermCacheService;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Concept Card 查缓存，未命中可配置为用 LLM 生成并缓存。
 */
@Service
public class ConceptCardServiceImpl implements ConceptCardService {

    private static final Pattern SLUG_PATTERN = Pattern.compile("[^a-z0-9_]");
    private static final TypeReference<Map<String, Object>> PAYLOAD_TYPE = new TypeReference<>() {};

    private final TermCacheService termCacheService;
    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    @Value("${app.concept-card.generate-on-miss:false}")
    private boolean generateOnMiss;

    @Value("${spring.ai.openai.chat.options.model:deepseek-chat}")
    private String modelForConcept;

    public ConceptCardServiceImpl(TermCacheService termCacheService,
                                  ChatClient.Builder chatClientBuilder,
                                  ObjectMapper objectMapper) {
        this.termCacheService = termCacheService;
        this.chatClient = chatClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Override
    public ConceptCardResponse lookup(String type, String lang, String key, String text) {
        String cacheKey = buildCacheKey(type, lang, key, text);
        if (cacheKey == null) {
            return null;
        }

        TermCache cached = termCacheService.getById(cacheKey);
        if (cached != null) {
            return fromTermCache(cached, cacheKey);
        }

        if (generateOnMiss && (key != null || (text != null && !text.isBlank()))) {
            return generateAndCache(type, lang, key, text, cacheKey);
        }
        return null;
    }

    private String buildCacheKey(String type, String lang, String key, String text) {
        String identifier;
        if (key != null && !key.isBlank()) {
            identifier = key.trim();
        } else if (text != null && !text.isBlank()) {
            identifier = slug(text.trim());
        } else {
            return null;
        }
        return type + ":" + identifier + ":" + lang;
    }

    private static String slug(String s) {
        String lower = s.toLowerCase(Locale.ROOT).replace(' ', '_');
        return SLUG_PATTERN.matcher(lower).replaceAll("");
    }

    private ConceptCardResponse fromTermCache(TermCache t, String cacheKey) {
        String keyPart = cacheKey.contains(":") ? cacheKey.split(":")[1] : cacheKey;
        String shortDesc = null;
        List<ConceptCardResponse.DetailItem> details = null;
        List<String> seeAlso = null;

        if (t.getPayloadJson() != null && !t.getPayloadJson().isBlank()) {
            try {
                Map<String, Object> payload = objectMapper.readValue(t.getPayloadJson(), PAYLOAD_TYPE);
                if (payload.containsKey("key")) keyPart = String.valueOf(payload.get("key"));
                if (payload.containsKey("short")) shortDesc = String.valueOf(payload.get("short"));
                if (payload.containsKey("details")) {
                    details = objectMapper.convertValue(payload.get("details"),
                            new TypeReference<List<ConceptCardResponse.DetailItem>>() {});
                }
                if (payload.containsKey("seeAlso")) {
                    seeAlso = objectMapper.convertValue(payload.get("seeAlso"),
                            new TypeReference<List<String>>() {});
                }
            } catch (Exception ignored) {
                // use defaults
            }
        }

        return ConceptCardResponse.builder()
                .key(keyPart)
                .title(t.getTitle())
                .shortDescription(shortDesc)
                .details(details)
                .seeAlso(seeAlso)
                .build();
    }

    private ConceptCardResponse generateAndCache(String type, String lang, String key, String text, String cacheKey) {
        String displayText = (text != null && !text.isBlank()) ? text : key;
        String systemPrompt = "You are an astronomy glossary assistant. Reply with a single JSON object only, no markdown. " +
                "Structure: {\"key\":\"identifier\", \"title\":\"display title\", \"short\":\"1-3 sentence definition\", " +
                "\"details\":[{\"label\":\"Meaning\",\"value\":\"...\"}, ...], \"seeAlso\":[\"term1\",\"term2\"]}. " +
                "Use " + ("zh".equalsIgnoreCase(lang) ? "Chinese" : "English") + " for title, short, and detail values.";
        String userPrompt = (type.equalsIgnoreCase("term") ? "Term" : "Symbol") + ": " + displayText;

        String response;
        try {
            response = chatClient.prompt()
                    .system(systemPrompt)
                    .user(userPrompt)
                    .call()
                    .content();
        } catch (Exception e) {
            return null;
        }

        ConceptCardResponse card = parseGeneratedResponse(response, cacheKey);
        if (card == null) return null;

        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(Map.of(
                    "key", card.getKey(),
                    "short", card.getShortDescription() != null ? card.getShortDescription() : "",
                    "details", card.getDetails() != null ? card.getDetails() : List.of(),
                    "seeAlso", card.getSeeAlso() != null ? card.getSeeAlso() : List.of()
            ));
        } catch (Exception e) {
            return card;
        }

        LocalDateTime now = LocalDateTime.now();
        TermCache term = new TermCache();
        term.setCacheKey(cacheKey);
        term.setType(type);
        term.setLanguage(lang);
        term.setTitle(card.getTitle() != null ? card.getTitle() : displayText);
        term.setPayloadJson(payloadJson);
        term.setCreatedAt(now);
        term.setUpdatedAt(now);
        termCacheService.save(term);
        return card;
    }

    private ConceptCardResponse parseGeneratedResponse(String content, String fallbackKey) {
        if (content == null || content.isBlank()) return null;
        String json = content.trim();
        if (json.startsWith("```")) {
            int start = json.indexOf('{');
            int end = json.lastIndexOf('}') + 1;
            if (start >= 0 && end > start) json = json.substring(start, end);
        }
        try {
            Map<String, Object> map = objectMapper.readValue(json, PAYLOAD_TYPE);
            String key = map.containsKey("key") ? String.valueOf(map.get("key")) : (fallbackKey.contains(":") ? fallbackKey.split(":")[1] : fallbackKey);
            String title = map.containsKey("title") ? String.valueOf(map.get("title")) : "";
            String shortDesc = map.containsKey("short") ? String.valueOf(map.get("short")) : null;
            List<ConceptCardResponse.DetailItem> details = map.containsKey("details")
                    ? objectMapper.convertValue(map.get("details"), new TypeReference<>() {})
                    : null;
            List<String> seeAlso = map.containsKey("seeAlso")
                    ? objectMapper.convertValue(map.get("seeAlso"), new TypeReference<>() {})
                    : null;
            return ConceptCardResponse.builder()
                    .key(key)
                    .title(title)
                    .shortDescription(shortDesc)
                    .details(details)
                    .seeAlso(seeAlso)
                    .build();
        } catch (Exception e) {
            return null;
        }
    }
}
