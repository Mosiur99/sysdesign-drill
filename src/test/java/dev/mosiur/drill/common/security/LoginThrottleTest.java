package dev.mosiur.drill.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class LoginThrottleTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-27T03:00:00Z"));
    private final LoginThrottle throttle = new LoginThrottle(clock);

    @Test
    void countsDownTriesAndBlocksAfterFiveFailures() {
        for (int left = 4; left >= 0; left--) {
            assertThat(throttle.isBlocked("10.0.0.1")).isFalse();
            assertThat(throttle.recordFailure("10.0.0.1")).isEqualTo(left);
        }

        assertThat(throttle.isBlocked("10.0.0.1")).isTrue();
        assertThat(throttle.isBlocked("10.0.0.2")).isFalse();
    }

    @Test
    void unblocksWhenTheMinuteIsOver() {
        for (int i = 0; i < LoginThrottle.MAX_FAILURES; i++) {
            throttle.recordFailure("10.0.0.1");
        }

        clock.advance(LoginThrottle.WINDOW);

        assertThat(throttle.isBlocked("10.0.0.1")).isFalse();
        assertThat(throttle.recordFailure("10.0.0.1")).isEqualTo(4);
    }

    @Test
    void successfulSignInClearsTheCount() {
        throttle.recordFailure("10.0.0.1");
        throttle.recordFailure("10.0.0.1");

        throttle.reset("10.0.0.1");

        assertThat(throttle.recordFailure("10.0.0.1")).isEqualTo(4);
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
