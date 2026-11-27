package dev.mosiur.drill.grading.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.mosiur.drill.problem.domain.Dimension;
import org.junit.jupiter.api.Test;

class RubricTest {

    @Test
    void coversEveryDimensionOnce() {
        assertThat(Rubric.CRITERIA).extracting(Rubric.Criterion::dimension).containsExactly(Dimension.values());
    }

    @Test
    void fullMarksAreAHundredAndNothingIsZero() {
        assertThat(Rubric.overall(all(4))).isEqualByComparingTo("100");
        assertThat(Rubric.overall(all(0))).isEqualByComparingTo("0");
        assertThat(Rubric.overall(Map.of())).isEqualByComparingTo("0");
    }

    @Test
    void weightsArchitectureAndDeepDiveMoreThanApi() {
        BigDecimal architecture = Rubric.overall(Map.of(Dimension.ARCHITECTURE, 4));
        BigDecimal api = Rubric.overall(Map.of(Dimension.API, 4));

        assertThat(architecture).isEqualByComparingTo("15.63");
        assertThat(api).isEqualByComparingTo("9.38");
    }

    @Test
    void averageAnswerLandsInTheMiddle() {
        assertThat(Rubric.overall(all(2))).isEqualByComparingTo("50");
    }

    private static Map<Dimension, Integer> all(int score) {
        return Arrays.stream(Dimension.values()).collect(Collectors.toMap(Function.identity(), dimension -> score));
    }
}
