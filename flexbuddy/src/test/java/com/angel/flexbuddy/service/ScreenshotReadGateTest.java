package com.angel.flexbuddy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.angel.flexbuddy.exception.ScreenshotBusyException;

class ScreenshotReadGateTest {

    @Test
    void aSecondReadWaitsThenIsToldToTryAgain() throws InterruptedException {
        ScreenshotReadGate gate = new ScreenshotReadGate(Duration.ofMillis(100));
        gate.permit().acquire();
        try {
            assertThatThrownBy(() -> gate.read(() -> "read"))
                    .isInstanceOf(ScreenshotBusyException.class)
                    .hasMessage("Another screenshot is being read. Try again in a moment.");
        } finally {
            gate.permit().release();
        }
        assertThat(gate.permit().availablePermits()).isEqualTo(1);
    }

    @Test
    void theTurnIsGivenBackWhenAReadFails() {
        ScreenshotReadGate gate = new ScreenshotReadGate(Duration.ofMillis(100));

        assertThatThrownBy(() -> gate.read(() -> { throw new IllegalStateException("unreadable"); }))
                .isInstanceOf(IllegalStateException.class);

        assertThat(gate.read(() -> "next")).isEqualTo("next");
    }
}
