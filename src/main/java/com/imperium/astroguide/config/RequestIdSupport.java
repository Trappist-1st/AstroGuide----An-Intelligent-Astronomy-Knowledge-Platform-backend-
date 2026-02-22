package com.imperium.astroguide.config;

import jakarta.servlet.http.HttpServletRequest;

import java.util.UUID;

public final class RequestIdSupport {

    public static final String HEADER_REQUEST_ID = "X-Request-Id";
    public static final String ATTR_REQUEST_ID = "requestId";

    private RequestIdSupport() {
    }

    public static String newRequestId() {
        return "req_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
    }

    public static String resolve(HttpServletRequest request) {
        if (request == null) {
            return newRequestId();
        }
        Object attr = request.getAttribute(ATTR_REQUEST_ID);
        if (attr instanceof String value && !value.isBlank()) {
            return value;
        }
        String headerValue = request.getHeader(HEADER_REQUEST_ID);
        if (headerValue != null && !headerValue.isBlank()) {
            return headerValue;
        }
        String generated = newRequestId();
        request.setAttribute(ATTR_REQUEST_ID, generated);
        return generated;
    }
}
