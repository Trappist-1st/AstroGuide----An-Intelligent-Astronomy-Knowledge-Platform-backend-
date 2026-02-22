package com.imperium.astroguide.controller;

import com.imperium.astroguide.config.RequestIdSupport;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局校验异常处理：@Valid 失败时按 TDD 5.0.5 返回 error 结构。
 */
@RestControllerAdvice
public class GlobalValidationExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getDefaultMessage())
                .orElse("Validation failed");
        String field = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField())
                .orElse(null);

        Map<String, Object> err = new HashMap<>();
        err.put("code", "invalid_argument");
        err.put("message", message);
        err.put("requestId", resolveRequestId());
        if (field != null) {
            err.put("details", Map.of("field", field));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", err));
    }

    private static String resolveRequestId() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return RequestIdSupport.newRequestId();
        }
        HttpServletRequest request = attributes.getRequest();
        return RequestIdSupport.resolve(request);
    }
}
