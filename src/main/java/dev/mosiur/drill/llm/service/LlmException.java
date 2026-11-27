package dev.mosiur.drill.llm.service;

public class LlmException extends RuntimeException {

    public enum Reason {
        NOT_CONFIGURED,
        RATE_LIMITED,
        SERVER_ERROR,
        NETWORK_ERROR,
        REJECTED,
        INVALID_RESPONSE
    }

    private final Reason reason;
    private final int attempts;

    public LlmException(Reason reason, String message, int attempts) {
        super(message);
        this.reason = reason;
        this.attempts = attempts;
    }

    public Reason reason() {
        return reason;
    }

    public int attempts() {
        return attempts;
    }
}
