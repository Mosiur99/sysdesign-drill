package dev.mosiur.drill.grading.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

import dev.mosiur.drill.problem.domain.Dimension;

public final class Rubric {

    public record Criterion(Dimension dimension, double weight, String four) {
    }

    public static final List<Criterion> CRITERIA = List.of(
            new Criterion(Dimension.REQUIREMENTS, 1.0, "Functional and non-functional requirements stated, scope cut, "
                    + "and the numbers asked for (users, QPS, latency, durability)."),
            new Criterion(Dimension.ESTIMATION, 0.75, "QPS, storage and bandwidth worked out with clear assumptions, "
                    + "and the results used to drive the design."),
            new Criterion(Dimension.API, 0.75, "Endpoints or messages with request and response, idempotency and "
                    + "pagination where they matter."),
            new Criterion(Dimension.DATA_MODEL, 1.0, "Entities, keys and access patterns, storage choice justified, "
                    + "partition key chosen."),
            new Criterion(Dimension.ARCHITECTURE, 1.25, "Components and data flow that are coherent and cover every "
                    + "requirement, with a diagram."),
            new Criterion(Dimension.DEEP_DIVE, 1.25, "The hardest part of the problem solved in detail (for example "
                    + "hot keys, fan-out or ordering)."),
            new Criterion(Dimension.SCALE_FAILURE, 1.0, "Bottlenecks, replication, failure modes, retries and "
                    + "backpressure, monitoring."),
            new Criterion(Dimension.TRADEOFFS, 1.0, "Alternatives named, with why one was chosen and what it costs."));

    public static final int MAX_SCORE = 4;

    private Rubric() {
    }

    public static BigDecimal overall(Map<Dimension, Integer> scores) {
        double weighted = 0;
        double totalWeight = 0;
        for (Criterion criterion : CRITERIA) {
            weighted += criterion.weight() * scores.getOrDefault(criterion.dimension(), 0);
            totalWeight += criterion.weight();
        }
        return BigDecimal.valueOf(weighted / totalWeight / MAX_SCORE * 100).setScale(2, RoundingMode.HALF_UP);
    }
}
