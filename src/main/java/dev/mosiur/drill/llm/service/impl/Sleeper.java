package dev.mosiur.drill.llm.service.impl;

import java.time.Duration;

@FunctionalInterface
interface Sleeper {

    Sleeper REAL = duration -> {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting to retry", e);
        }
    };

    void sleep(Duration duration);
}
