package dev.mosiur.drill.grading.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

public final class FollowUpScore {

    public static final double POINTS_PER_QUESTION = 5;
    public static final double MAX_BONUS = 10;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    public record Answered(int originalScore, int score) {
    }

    private FollowUpScore() {
    }

    public static BigDecimal bonus(List<Answered> answers) {
        double bonus = 0;
        for (Answered answer : answers) {
            int gap = Math.max(1, Rubric.MAX_SCORE - answer.originalScore());
            int gain = Math.max(0, answer.score() - answer.originalScore());
            bonus += POINTS_PER_QUESTION * Math.min(gain, gap) / gap;
        }
        return BigDecimal.valueOf(Math.min(MAX_BONUS, bonus)).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal finalScore(BigDecimal overall, BigDecimal bonus) {
        return overall.add(bonus).min(HUNDRED);
    }
}
