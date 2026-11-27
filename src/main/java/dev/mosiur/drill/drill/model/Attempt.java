package dev.mosiur.drill.drill.model;

import java.time.LocalDate;

public record Attempt(double lastScore, LocalDate lastAttemptedOn, LocalDate nextDue, boolean retired) {

    public boolean isDueBy(LocalDate day) {
        return !retired && nextDue != null && !nextDue.isAfter(day);
    }
}
