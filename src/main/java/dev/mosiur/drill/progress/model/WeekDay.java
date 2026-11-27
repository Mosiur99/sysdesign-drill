package dev.mosiur.drill.progress.model;

import java.time.LocalDate;

public record WeekDay(LocalDate day, boolean today, Integer score, boolean submitted) {

    public boolean pending() {
        return submitted && score == null;
    }
}
