package dev.mosiur.drill.progress.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class ProblemProgressTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 27);

    private final ProblemProgress progress = new ProblemProgress(7);

    @Test
    void weakScoreComesBackInThreeDays() {
        progress.record(new BigDecimal("49.99"), DAY);

        assertThat(progress.getNextDue()).isEqualTo(DAY.plusDays(3));
        assertThat(progress.getAttempts()).isEqualTo(1);
        assertThat(progress.getLastAttemptedOn()).isEqualTo(DAY);
    }

    @Test
    void middleScoreComesBackInTenDays() {
        progress.record(new BigDecimal("50"), DAY);
        assertThat(progress.getNextDue()).isEqualTo(DAY.plusDays(10));

        progress.record(new BigDecimal("74.99"), DAY.plusDays(10));
        assertThat(progress.getNextDue()).isEqualTo(DAY.plusDays(20));
    }

    @Test
    void firstStrongScoreComesBackInThirtyDays() {
        progress.record(new BigDecimal("75"), DAY);

        assertThat(progress.getNextDue()).isEqualTo(DAY.plusDays(30));
        assertThat(progress.isRetired()).isFalse();
    }

    @Test
    void secondStrongScoreRetiresTheProblem() {
        progress.record(new BigDecimal("80"), DAY);
        progress.record(new BigDecimal("62"), DAY.plusDays(30));
        progress.record(new BigDecimal("77"), DAY.plusDays(40));

        assertThat(progress.isRetired()).isTrue();
        assertThat(progress.getNextDue()).isNull();
        assertThat(progress.getBestScore()).isEqualByComparingTo("80");
        assertThat(progress.getLastScore()).isEqualByComparingTo("77");
        assertThat(progress.getAttempts()).isEqualTo(3);
    }
}
