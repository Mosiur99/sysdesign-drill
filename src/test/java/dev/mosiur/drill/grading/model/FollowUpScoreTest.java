package dev.mosiur.drill.grading.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import dev.mosiur.drill.grading.model.FollowUpScore.Answered;
import org.junit.jupiter.api.Test;

class FollowUpScoreTest {

    @Test
    void closingBothGapsAddsTheFullTenPoints() {
        assertThat(FollowUpScore.bonus(List.of(new Answered(1, 4), new Answered(2, 4)))).isEqualByComparingTo("10");
    }

    @Test
    void partialImprovementEarnsItsShareOfFivePoints() {
        assertThat(FollowUpScore.bonus(List.of(new Answered(1, 2)))).isEqualByComparingTo("1.67");
        assertThat(FollowUpScore.bonus(List.of(new Answered(2, 3)))).isEqualByComparingTo("2.50");
    }

    @Test
    void aWeakerFollowUpNeverCostsPoints() {
        assertThat(FollowUpScore.bonus(List.of(new Answered(3, 1), new Answered(4, 4)))).isEqualByComparingTo("0");
    }

    @Test
    void finalScoreIsCappedAtAHundred() {
        assertThat(FollowUpScore.finalScore(new BigDecimal("96.88"), new BigDecimal("10")))
                .isEqualByComparingTo("100");
        assertThat(FollowUpScore.finalScore(new BigDecimal("61.25"), new BigDecimal("4.17")))
                .isEqualByComparingTo("65.42");
    }
}
