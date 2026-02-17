package com.imperium.astroguide.controller;

import com.imperium.astroguide.model.dto.response.ConceptCardResponse;
import com.imperium.astroguide.service.ConceptCardService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Concept Cards 点读解释，对应 TDD 5.4。
 * V0 约定：读写均需 X-Client-Id。
 */
@RestController
@RequestMapping("/api/v0/concepts")
public class ConceptCardsController {

    private static final String HEADER_CLIENT_ID = "X-Client-Id";

    private final ConceptCardService conceptCardService;

    public ConceptCardsController(ConceptCardService conceptCardService) {
        this.conceptCardService = conceptCardService;
    }

    /**
     * 查询概念卡片。优先缓存；未命中且开启生成时尝试生成并缓存。
     * TDD: GET /concepts/lookup
     * Query: type（必填 term|sym）, lang（必填 en|zh）, key（可选）, text（可选）
     */
    @GetMapping("/lookup")
    public ResponseEntity<?> lookup(
            @RequestHeader(value = HEADER_CLIENT_ID, required = false) String clientId,
            @RequestParam String type,
            @RequestParam String lang,
            @RequestParam(required = false) String key,
            @RequestParam(required = false) String text) {

        if (clientId == null || clientId.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "invalid_argument", "X-Client-Id is required", "header", HEADER_CLIENT_ID);
        }
        if (type == null || type.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "invalid_argument", "type is required", "field", "type");
        }
        if (!type.equalsIgnoreCase("term") && !type.equalsIgnoreCase("sym")) {
            return error(HttpStatus.BAD_REQUEST, "invalid_argument", "type must be term or sym", "field", "type");
        }
        if (lang == null || lang.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "invalid_argument", "lang is required", "field", "lang");
        }
        if (!lang.equalsIgnoreCase("en") && !lang.equalsIgnoreCase("zh")) {
            return error(HttpStatus.BAD_REQUEST, "invalid_argument", "lang must be en or zh", "field", "lang");
        }
        if ((key == null || key.isBlank()) && (text == null || text.isBlank())) {
            return error(HttpStatus.BAD_REQUEST, "invalid_argument", "key or text is required", "field", "key");
        }

        ConceptCardResponse card = conceptCardService.lookup(
                type.trim().toLowerCase(),
                lang.trim().toLowerCase(),
                key != null ? key.trim() : null,
                text != null ? text.trim() : null);

        if (card == null) {
            return error(HttpStatus.NOT_FOUND, "not_found", "Concept card not found and generation disabled or failed", null, null);
        }
        return ResponseEntity.ok(card);
    }

    private static ResponseEntity<Map<String, Object>> error(
            HttpStatus status, String code, String message, String detailsKey, Object detailsValue) {
        Map<String, Object> err = new HashMap<>();
        err.put("code", code);
        err.put("message", message);
        err.put("requestId", "req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        if (detailsKey != null && detailsValue != null) {
            err.put("details", Map.of(detailsKey, detailsValue));
        }
        return ResponseEntity.status(status).body(Map.of("error", err));
    }
}
