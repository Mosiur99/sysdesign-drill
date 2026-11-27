package dev.mosiur.drill.progress.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

public record NextRetry(String title, LocalDate due, BigDecimal lastScore) {

    public int lastScoreRounded() {
        return lastScore.setScale(0, RoundingMode.HALF_UP).intValue();
    }
}
