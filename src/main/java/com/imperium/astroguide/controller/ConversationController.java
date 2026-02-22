package com.imperium.astroguide.controller;

import com.imperium.astroguide.config.RequestIdSupport;
import com.imperium.astroguide.model.dto.request.CreateConversationRequest;
import com.imperium.astroguide.model.dto.response.*;
import com.imperium.astroguide.model.entity.Conversation;
import com.imperium.astroguide.model.entity.Message;
import com.imperium.astroguide.service.ConversationService;
import com.imperium.astroguide.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 会话接口，按 TDD 5.1：创建、列表、详情。
 */
@RestController
@RequestMapping("/api/v0/conversations")
@Tag(name = "Conversations", description = "会话管理接口")
public class ConversationController {

    private static final String HEADER_CLIENT_ID = "X-Client-Id";
    private static final int LIST_LIMIT_MAX = 50;
    private static final int DETAIL_LIMIT_MAX = 200;
    private static final int PREVIEW_MAX_LEN = 80;

    private final ConversationService conversationService;
    private final MessageService messageService;

    public ConversationController(ConversationService conversationService, MessageService messageService) {
        this.conversationService = conversationService;
        this.messageService = messageService;
    }

    /**
     * 创建会话。TDD: POST /conversations
     */
    @PostMapping
        @Operation(summary = "创建会话", description = "创建一个新的会话，必须携带 X-Client-Id")
    public ResponseEntity<?> create(
            @Parameter(description = "客户端标识", required = true)
            @RequestHeader(value = HEADER_CLIENT_ID, required = false) String clientId,
            HttpServletRequest request,
            @RequestBody(required = false) CreateConversationRequest body) {

        if (clientId == null || clientId.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "invalid_argument", "X-Client-Id is required", "header", HEADER_CLIENT_ID,
                    request);
        }

        String title = (body != null && body.getTitle() != null) ? body.getTitle() : null;
        String id = "c_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        LocalDateTime now = LocalDateTime.now();

        Conversation conversation = new Conversation();
        conversation.setId(id);
        conversation.setTitle(title);
        conversation.setClientId(clientId);
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);
        conversationService.save(conversation);

        ConversationResponse resp = ConversationResponse.builder()
                .id(id)
                .title(title)
                .createdAt(now)
                .updatedAt(now)
                .build();
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    /**
     * 列出当前 clientId 的会话。TDD: GET /conversations
     * Query: limit（默认 20，最大 50）, cursor（可选游标）
     */
    @GetMapping
        @Operation(summary = "会话列表", description = "按更新时间倒序获取当前客户端会话列表")
    public ResponseEntity<?> list(
            @Parameter(description = "客户端标识", required = true)
            @RequestHeader(value = HEADER_CLIENT_ID, required = false) String clientId,
            HttpServletRequest request,
            @Parameter(description = "分页大小，默认 20，最大 50")
            @RequestParam(defaultValue = "20") int limit,
            @Parameter(description = "分页游标，传入上次返回列表末尾会话 ID")
            @RequestParam(required = false) String cursor) {

        if (clientId == null || clientId.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "invalid_argument", "X-Client-Id is required", "header", HEADER_CLIENT_ID,
                    request);
        }

        int size = Math.min(Math.max(1, limit), LIST_LIMIT_MAX);

        List<Conversation> list;
        String nextCursor = null;

        if (cursor != null && !cursor.isBlank()) {
            Conversation cursorConv = conversationService.getById(cursor);
            if (cursorConv == null || !cursorConv.getClientId().equals(clientId)) {
                return error(HttpStatus.BAD_REQUEST, "invalid_argument", "Invalid cursor", "cursor", cursor, request);
            }
            list = conversationService.lambdaQuery()
                    .eq(Conversation::getClientId, clientId)
                    .apply("(updated_at < {0} OR (updated_at = {0} AND id < {1}))",
                            cursorConv.getUpdatedAt(), cursor)
                    .orderByDesc(Conversation::getUpdatedAt)
                    .orderByDesc(Conversation::getId)
                    .last("LIMIT " + (size + 1))
                    .list();
        } else {
            list = conversationService.lambdaQuery()
                    .eq(Conversation::getClientId, clientId)
                    .orderByDesc(Conversation::getUpdatedAt)
                    .orderByDesc(Conversation::getId)
                    .last("LIMIT " + (size + 1))
                    .list();
        }

        if (list.size() > size) {
            nextCursor = list.get(size - 1).getId();
            list = list.subList(0, size);
        }

        List<String> convIds = list.stream().map(Conversation::getId).collect(Collectors.toList());
        Map<String, String> previewByConv = new HashMap<>();
        for (String cid : convIds) {
            Message last = messageService.lambdaQuery()
                    .eq(Message::getConversationId, cid)
                    .orderByDesc(Message::getCreatedAt)
                    .last("LIMIT 1")
                    .one();
            if (last != null && last.getContent() != null) {
                String content = last.getContent();
                previewByConv.put(cid, content.length() > PREVIEW_MAX_LEN ? content.substring(0, PREVIEW_MAX_LEN) + "..." : content);
            }
        }

        List<ConversationListItemDto> items = list.stream()
                .map(c -> ConversationListItemDto.builder()
                        .id(c.getId())
                        .title(c.getTitle())
                        .createdAt(c.getCreatedAt())
                        .updatedAt(c.getUpdatedAt())
                        .lastMessagePreview(previewByConv.get(c.getId()))
                        .build())
                .collect(Collectors.toList());

        return ResponseEntity.ok(ConversationListResponse.builder().items(items).nextCursor(nextCursor).build());
    }

    /**
     * 获取会话详情 + 最近消息。TDD: GET /conversations/{conversationId}
     * Query: limit（默认 50，最大 200）, before（可选，消息 id 向前翻页）
     */
    @GetMapping("/{conversationId}")
        @Operation(summary = "会话详情", description = "获取会话详情及最近消息，支持 before 向前翻页")
    public ResponseEntity<?> getDetail(
            @Parameter(description = "会话 ID", required = true)
            @PathVariable String conversationId,
            @Parameter(description = "客户端标识", required = true)
            @RequestHeader(value = HEADER_CLIENT_ID, required = false) String clientId,
            HttpServletRequest request,
            @Parameter(description = "返回消息数量，默认 50，最大 200")
            @RequestParam(defaultValue = "50") int limit,
            @Parameter(description = "向前翻页锚点消息 ID")
            @RequestParam(required = false) String before) {

        if (clientId == null || clientId.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "invalid_argument", "X-Client-Id is required", "header", HEADER_CLIENT_ID,
                    request);
        }

        Conversation conversation = conversationService.getById(conversationId);
        if (conversation == null) {
            return error(HttpStatus.NOT_FOUND, "not_found", "Conversation not found", null, null, request);
        }
        if (!conversation.getClientId().equals(clientId)) {
            return error(HttpStatus.FORBIDDEN, "forbidden", "clientId does not match conversation", null, null, request);
        }

        int size = Math.min(Math.max(1, limit), DETAIL_LIMIT_MAX);

        List<Message> messages;
        String nextBefore = null;

        if (before != null && !before.isBlank()) {
            Message beforeMsg = messageService.getById(before);
            if (beforeMsg == null || !beforeMsg.getConversationId().equals(conversationId)) {
                return error(HttpStatus.BAD_REQUEST, "invalid_argument", "Invalid before", "before", before, request);
            }
            messages = messageService.lambdaQuery()
                    .eq(Message::getConversationId, conversationId)
                    .lt(Message::getCreatedAt, beforeMsg.getCreatedAt())
                    .orderByDesc(Message::getCreatedAt)
                    .last("LIMIT " + (size + 1))
                    .list();
        } else {
            messages = messageService.lambdaQuery()
                    .eq(Message::getConversationId, conversationId)
                    .orderByDesc(Message::getCreatedAt)
                    .last("LIMIT " + (size + 1))
                    .list();
        }

        if (messages.size() > size) {
            nextBefore = messages.get(size - 1).getId();
            messages = messages.subList(0, size);
        }

        List<MessageInConversationDto> messageDtos = messages.stream()
                .map(m -> MessageInConversationDto.builder()
                        .id(m.getId())
                        .role(m.getRole())
                        .content(m.getContent())
                        .difficulty(m.getDifficulty())
                        .language(m.getLanguage())
                        .status(m.getStatus())
                        .promptTokens(m.getPromptTokens())
                        .completionTokens(m.getCompletionTokens())
                        .estimatedCostUsd(m.getEstimatedCostUsd())
                        .createdAt(m.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        ConversationResponse convResp = ConversationResponse.builder()
                .id(conversation.getId())
                .title(conversation.getTitle())
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();

        return ResponseEntity.ok(ConversationDetailResponse.builder()
                .conversation(convResp)
                .messages(messageDtos)
                .nextBefore(nextBefore)
                .build());
    }

    private static ResponseEntity<Map<String, Object>> error(
            HttpStatus status, String code, String message, String detailsKey, Object detailsValue,
            HttpServletRequest request) {
        Map<String, Object> err = new HashMap<>();
        err.put("code", code);
        err.put("message", message);
        err.put("requestId", RequestIdSupport.resolve(request));
        if (detailsKey != null && detailsValue != null) {
            err.put("details", Map.of(detailsKey, detailsValue));
        }
        return ResponseEntity.status(status).body(Map.of("error", err));
    }
}
