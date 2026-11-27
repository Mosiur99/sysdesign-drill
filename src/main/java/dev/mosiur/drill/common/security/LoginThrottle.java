package dev.mosiur.drill.common.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class LoginThrottle {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(1);

    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public LoginThrottle(Clock clock) {
        this.clock = clock;
    }

    public boolean isBlocked(String client) {
        Window window = windows.get(client);
        return window != null && !window.isOver(clock.instant()) && window.failures() >= MAX_FAILURES;
    }

    public int recordFailure(String client) {
        Instant now = clock.instant();
        Window window = windows.compute(client, (key, current) -> current == null || current.isOver(now)
                ? new Window(now, 1)
                : new Window(current.start(), current.failures() + 1));
        return Math.max(0, MAX_FAILURES - window.failures());
    }

    public void reset(String client) {
        windows.remove(client);
    }

    private record Window(Instant start, int failures) {

        boolean isOver(Instant now) {
            return !now.isBefore(start.plus(WINDOW));
        }
    }
}
