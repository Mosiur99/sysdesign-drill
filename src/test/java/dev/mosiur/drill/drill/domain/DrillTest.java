package dev.mosiur.drill.drill.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;

import dev.mosiur.drill.problem.domain.Problem;
import org.junit.jupiter.api.Test;

class DrillTest {

    private static final Instant OPENED = Instant.parse("2026-11-27T03:00:00Z");
    private static final Instant LATER = OPENED.plusSeconds(2_700);

    private final Drill drill = new Drill(new Problem("url-shortener"), LocalDate.of(2026, 11, 27), DrillKind.NEW);

    @Test
    void startsOnceAndKeepsTheFirstStartTime() {
        drill.start(OPENED);
        drill.start(LATER);

        assertThat(drill.getStatus()).isEqualTo(DrillStatus.IN_PROGRESS);
        assertThat(drill.getStartedAt()).isEqualTo(OPENED);
    }

    @Test
    void submittingMovesToGradingAndLocksTheAnswer() {
        drill.start(OPENED);

        drill.submit(LATER);

        assertThat(drill.getStatus()).isEqualTo(DrillStatus.GRADING);
        assertThat(drill.getSubmittedAt()).isEqualTo(LATER);
        assertThat(drill.isEditable()).isFalse();
    }

    @Test
    void submittingWithoutOpeningStartsAndSubmitsAtTheSameTime() {
        drill.submit(LATER);

        assertThat(drill.getStartedAt()).isEqualTo(LATER);
        assertThat(drill.getStatus()).isEqualTo(DrillStatus.GRADING);
    }

    @Test
    void cannotSubmitTwice() {
        drill.submit(LATER);

        assertThatThrownBy(() -> drill.submit(LATER)).isInstanceOf(IllegalStateException.class);
    }
}
