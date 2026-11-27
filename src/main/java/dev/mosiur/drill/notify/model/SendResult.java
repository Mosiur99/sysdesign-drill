package dev.mosiur.drill.notify.model;

import java.time.Duration;

public record SendResult(Outcome outcome, String detail, Duration retryAfter) {

    public enum Outcome {
        SENT,
        RETRY,
        REJECTED
    }

    public static SendResult sent() {
        return new SendResult(Outcome.SENT, null, null);
    }

    public static SendResult retry(String detail, Duration retryAfter) {
        return new SendResult(Outcome.RETRY, detail, retryAfter);
    }

    public static SendResult rejected(String detail) {
        return new SendResult(Outcome.REJECTED, detail, null);
    }
}
