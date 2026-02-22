package com.imperium.astroguide.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RequestIdFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        String requestId = request.getHeader(RequestIdSupport.HEADER_REQUEST_ID);
        if (requestId == null || requestId.isBlank()) {
            requestId = RequestIdSupport.newRequestId();
        }
        request.setAttribute(RequestIdSupport.ATTR_REQUEST_ID, requestId);
        response.setHeader(RequestIdSupport.HEADER_REQUEST_ID, requestId);
        MDC.put(RequestIdSupport.ATTR_REQUEST_ID, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(RequestIdSupport.ATTR_REQUEST_ID);
        }
    }
}
