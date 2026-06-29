package com.imperium.astroguide.ai.orchestrator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StreamTerminalStateTest {

    @Test
    void tryFinalize_onlyFirstStatusWins() {
        StreamTerminalState state = new StreamTerminalState();
        assertTrue(state.tryFinalize(StreamTerminalState.Status.CANCELLED));
        assertFalse(state.tryFinalize(StreamTerminalState.Status.DONE));
        assertFalse(state.tryFinalize(StreamTerminalState.Status.ERROR));
        assertEquals(StreamTerminalState.Status.CANCELLED, state.current());
    }
}
