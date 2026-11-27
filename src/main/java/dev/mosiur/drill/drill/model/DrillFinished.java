package dev.mosiur.drill.drill.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DrillFinished(long drillId, long problemId, LocalDate day, BigDecimal finalScore) {
}
