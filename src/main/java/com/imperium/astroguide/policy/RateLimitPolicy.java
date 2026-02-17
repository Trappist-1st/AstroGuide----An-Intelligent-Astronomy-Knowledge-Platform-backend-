package com.imperium.astroguide.policy;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 限流策略：按 clientId + IP，例如 20 req / 10 min，对应 TDD 7、10.1。
 */
@Component
public class RateLimitPolicy {

    /** 时间窗口（毫秒）：10 分钟 */
    public static final long WINDOW_MS = 10 * 60 * 1000L;

    /** 窗口内最大请求数 */
    public static final int MAX_REQUESTS_PER_WINDOW = 20;

    private final Map<String, Window> keyToWindow = new ConcurrentHashMap<>();

    /**
     * 检查是否允许请求；若允许则记录一次。
     *
     * @param clientId X-Client-Id
     * @param clientIp 客户端 IP（如 request.getRemoteAddr()）
     * @return true 允许，false 应返回 429
     */
    public boolean allow(String clientId, String clientIp) {
        String key = (clientId != null ? clientId : "") + "|" + (clientIp != null ? clientIp : "");
        long now = System.currentTimeMillis();
        Window w = keyToWindow.compute(key, (k, old) -> {
            if (old == null || now - old.startMs > WINDOW_MS) {
                return new Window(now, 1);
            }
            if (old.count >= MAX_REQUESTS_PER_WINDOW) {
                return old;
            }
            return new Window(old.startMs, old.count + 1);
        });
        return w.count <= MAX_REQUESTS_PER_WINDOW;
    }

    private record Window(long startMs, int count) {}
}
