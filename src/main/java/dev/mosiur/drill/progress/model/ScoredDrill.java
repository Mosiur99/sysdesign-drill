package dev.mosiur.drill.progress.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import dev.mosiur.drill.problem.domain.Topic;

public record ScoredDrill(long drillId, LocalDate day, boolean retry, long problemId, String title, BigDecimal score,
                          Set<Topic> topics, Map<String, Integer> sections) {

    public double scoreValue() {
        return score.doubleValue();
    }
}
