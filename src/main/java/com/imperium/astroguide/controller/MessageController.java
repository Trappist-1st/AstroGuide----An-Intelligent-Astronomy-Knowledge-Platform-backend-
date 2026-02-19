package com.imperium.astroguide.controller;

import com.imperium.astroguide.model.dto.request.SubmitMessageRequest;
import com.imperium.astroguide.model.dto.response.SubmitMessageResponse;
import com.imperium.astroguide.model.entity.Conversation;
import com.imperium.astroguide.model.entity.Message;
import com.imperium.astroguide.service.ConversationService;
import com.imperium.astroguide.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * 消息接口：提交用户消息等，按 TDD 5.2。
 */
@RestController
@RequestMapping("/api/v0/conversations")
@Tag(name = "Messages", description = "消息提交接口")
public class MessageController {

    private static final String HEADER_CLIENT_ID = "X-Client-Id";
    private static final String STREAM_PATH_TEMPLATE = "/api/v0/conversations/%s/messages/%s/stream";

    private final ConversationService conversationService;
    private final MessageService messageService;

    public MessageController(ConversationService conversationService, MessageService messageService) {
        this.conversationService = conversationService;
        this.messageService = messageService;
    }

    /**
     * 提交用户消息，并创建一次“待生成”的 assistant 回复任务。
     * TDD: POST /conversations/{conversationId}/messages
     * 客户端随后 GET streamUrl 获取流式输出。
     */
    @PostMapping("/{conversationId}/messages")
        @Operation(summary = "提交消息", description = "提交用户消息并创建 assistant queued 占位消息")
    public ResponseEntity<?> submitMessage(
            @Parameter(description = "会话 ID", required = true)
            @PathVariable String conversationId,
            @Parameter(description = "客户端标识", required = true)
            @RequestHeader(value = HEADER_CLIENT_ID, required = false) String clientId,
            @Valid @RequestBody SubmitMessageRequest body) {

        if (clientId == null || clientId.isBlank()) {
            return error(HttpStatus.BAD_REQUEST, "invalid_argument", "X-Client-Id is required", "header", HEADER_CLIENT_ID);
        }

        String content = body.getContent();

        Conversation conversation = conversationService.getById(conversationId);
        if (conversation == null) {
            return error(HttpStatus.NOT_FOUND, "not_found", "Conversation not found", null, null);
        }
        if (!Objects.equals(conversation.getClientId(), clientId)) {
            return error(HttpStatus.FORBIDDEN, "forbidden", "clientId does not match conversation", null, null);
        }

        // 幂等：同一会话下相同 clientMessageId 且相同 content 才返回同一 messageId（仅对重试生效；新消息必生成新 ID）
        String clientMessageId = body.getClientMessageId();
        if (clientMessageId != null && !clientMessageId.isBlank()) {
            Message existing = messageService.lambdaQuery()
                    .eq(Message::getConversationId, conversationId)
                    .eq(Message::getClientMessageId, clientMessageId)
                    .eq(Message::getRole, "user")
                    .last("LIMIT 1")
                    .one();
            if (existing != null && Objects.equals(existing.getContent(), content)) {
                ensureAssistantPlaceholder(conversationId, existing.getId(), existing.getDifficulty(), existing.getLanguage());
                String streamUrl = String.format(STREAM_PATH_TEMPLATE, conversationId, existing.getId());
                return ResponseEntity.status(HttpStatus.ACCEPTED)
                        .body(SubmitMessageResponse.builder()
                                .messageId(existing.getId())
                                .streamUrl(streamUrl)
                                .status("queued")
                                .build());
            }
        }

        String messageId = "msg_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        LocalDateTime now = LocalDateTime.now();

        String difficulty = body.getDifficulty() == null || body.getDifficulty().isBlank() ? "intermediate" : body.getDifficulty();
        String language = body.getLanguage() == null || body.getLanguage().isBlank() ? "en" : body.getLanguage();

        Message message = new Message();
        message.setId(messageId);
        message.setConversationId(conversationId);
        message.setRole("user");
        message.setContent(content);
        message.setDifficulty(difficulty);
        message.setLanguage(language);
        message.setStatus("done");
        message.setClientMessageId(clientMessageId);
        message.setCreatedAt(now);

        messageService.save(message);

        // assistant 占位消息：queued
        ensureAssistantPlaceholder(conversationId, messageId, difficulty, language);

        conversation.setUpdatedAt(now);
        conversationService.updateById(conversation);

        String streamUrl = String.format(STREAM_PATH_TEMPLATE, conversationId, messageId);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(SubmitMessageResponse.builder()
                        .messageId(messageId)
                        .streamUrl(streamUrl)
                        .status("queued")
                        .build());
    }

    private void ensureAssistantPlaceholder(String conversationId, String userMessageId, String difficulty, String language) {
        String assistantId = userMessageId + "_a";
        boolean exists = messageService.getById(assistantId) != null;
        if (exists) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        Message assistant = new Message();
        assistant.setId(assistantId);
        assistant.setConversationId(conversationId);
        assistant.setRole("assistant");
        assistant.setContent("");
        assistant.setDifficulty(difficulty);
        assistant.setLanguage(language);
        assistant.setStatus("queued");
        assistant.setCreatedAt(now);
        messageService.save(assistant);
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
