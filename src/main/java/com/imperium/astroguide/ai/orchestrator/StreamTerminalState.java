package com.imperium.astroguide.ai.orchestrator;

import java.util.concurrent.atomic.AtomicReference;

/**
 * SSE 流终态 CAS：done / error / cancelled 仅第一个胜出，避免 cancel 后被 done 覆盖。
 */
public final class StreamTerminalState {

    public enum Status {
        NONE, DONE, ERROR, CANCELLED
    }

    private final AtomicReference<Status> status = new AtomicReference<>(Status.NONE);

    /**
     * @return true 若本次成功占用终态（应执行落库等副作用）
     */
    public boolean tryFinalize(Status candidate) {
        if (candidate == null || candidate == Status.NONE) {
            return false;
        }
        return status.compareAndSet(Status.NONE, candidate);
    }

    public Status current() {
        return status.get();
    }

    public boolean isFinalized() {
        return status.get() != Status.NONE;
    }
}
