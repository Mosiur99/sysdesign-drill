package dev.mosiur.drill.answer.model;

import dev.mosiur.drill.drill.domain.DrillStatus;

public record SubmissionView(long drillId, String title, DrillStatus status, int wordCount, Integer secondsSpent) {

    public Integer minutesSpent() {
        return secondsSpent == null ? null : Math.round(secondsSpent / 60f);
    }
}
