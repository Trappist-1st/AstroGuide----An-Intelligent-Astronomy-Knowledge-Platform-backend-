package com.imperium.astroguide.ai.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolPolicyServiceTest {

    private ToolPolicyService policyService;
    private ToolDefinition definition;

    @BeforeEach
    void setUp() {
        policyService = new ToolPolicyService();
        ReflectionTestUtils.setField(policyService, "maxCallsTotal", 2);
        definition = ToolDefinition.builder()
                .name("search_wikipedia")
                .description("test")
                .enabled(true)
                .timeoutMs(1000)
                .maxCallsPerRun(1)
                .sourceBean("wikipediaTool")
                .build();
    }

    @Test
    void allowCall_respectsTotalBudget() {
        Map<String, Integer> calls = new HashMap<>();
        assertTrue(policyService.allowCall("search_wikipedia", 0, calls, definition));
        assertFalse(policyService.allowCall("search_wikipedia", 2, calls, definition));
    }

    @Test
    void allowCall_respectsPerToolLimit() {
        Map<String, Integer> calls = new HashMap<>();
        calls.put("search_wikipedia", 1);
        assertFalse(policyService.allowCall("search_wikipedia", 1, calls, definition));
    }
}
