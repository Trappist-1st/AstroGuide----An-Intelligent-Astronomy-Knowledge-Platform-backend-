package com.imperium.astroguide.ai.multiagent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuestionRouterServiceTest {

    private final QuestionRouterService router = new QuestionRouterService(true, 120, 2);

    @Test
    void route_simpleQuestion() {
        RouteDecision decision = router.route("什么是黑洞？");
        assertEquals(RouteMode.SIMPLE, decision.mode());
    }

    @Test
    void route_complexByLength() {
        String longQuestion = "请详细解释".repeat(30);
        RouteDecision decision = router.route(longQuestion);
        assertEquals(RouteMode.COMPLEX, decision.mode());
        assertEquals("long_question", decision.reasonCode());
    }

    @Test
    void route_complexByKeyword() {
        RouteDecision decision = router.route("请对比恒星演化与黑洞形成的过程");
        assertEquals(RouteMode.COMPLEX, decision.mode());
        assertEquals("complex_keyword", decision.reasonCode());
    }

    @Test
    void route_complexByMultiQuestion() {
        RouteDecision decision = router.route("黑洞是什么？白洞存在吗？");
        assertEquals(RouteMode.COMPLEX, decision.mode());
        assertEquals("multi_question", decision.reasonCode());
    }
}
