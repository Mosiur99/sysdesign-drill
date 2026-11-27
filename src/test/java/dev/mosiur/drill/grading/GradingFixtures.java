package dev.mosiur.drill.grading;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import dev.mosiur.drill.grading.model.ChecklistPoint;
import dev.mosiur.drill.grading.model.GradingInput;
import dev.mosiur.drill.problem.domain.Dimension;

public final class GradingFixtures {

    public static final Map<String, String> SECTIONS = sections();

    private GradingFixtures() {
    }

    public static GradingInput input() {
        return new GradingInput(5, 9, "Design a URL shortener", "Shorten long links.", List.of(
                new ChecklistPoint(101, "base62-ids", "deep_dive", "Generates short codes from a counter in base62"),
                new ChecklistPoint(102, "cache-reads", "architecture", "Caches hot redirects")), SECTIONS);
    }

    public static String gradeJson(Map<Dimension, Integer> scores, Map<Dimension, String> evidence) {
        String dimensions = Arrays.stream(Dimension.values())
                .map(dimension -> """
                        {"dimension": "%s", "score": %d, "evidence": "%s", "missing": ["more detail"]}"""
                        .formatted(dimension.code(), scores.getOrDefault(dimension, 2),
                                evidence.getOrDefault(dimension, defaultEvidence(dimension))))
                .collect(Collectors.joining(", "));
        return """
                {"summary": "Clear API, thin on failure handling.",
                 "dimensions": [%s],
                 "key_points": [{"code": "base62-ids", "covered": true}, {"code": "cache-reads", "covered": false}],
                 "strengths": ["Clear API"],
                 "improvements": ["Add a cache for hot links.", "Explain the ID scheme.", "Cover a database outage."],
                 "follow_ups": [{"dimension": "scale_failure", "question": "What happens when the database is down?"},
                                {"dimension": "deep_dive", "question": "How do you avoid two links getting one code?"}]}
                """.formatted(dimensions);
    }

    private static String defaultEvidence(Dimension dimension) {
        return switch (dimension) {
            case REQUIREMENTS -> "Users shorten long links";
            case ESTIMATION -> "100M new links a month";
            case API -> "POST /links returns the short code";
            case DATA_MODEL -> "links table keyed by code";
            case ARCHITECTURE -> "api --> db";
            case DEEP_DIVE -> "a counter encoded in base62";
            case SCALE_FAILURE -> "read replicas for redirects";
            case TRADEOFFS -> "counter over random codes";
        };
    }

    private static Map<String, String> sections() {
        Map<String, String> sections = new LinkedHashMap<>();
        sections.put("requirements", "Users shorten long links and open them. Reads dominate.");
        sections.put("estimation", "100M new links a month, 10:1 reads.");
        sections.put("api", "POST /links returns the short code.\nGET /{code} redirects.");
        sections.put("data_model", "A links table keyed by code.");
        sections.put("architecture", "flowchart LR\n  api --> db");
        sections.put("deep_dive", "Codes come from a counter encoded in base62.");
        sections.put("scale_failure", "Read replicas for redirects.");
        sections.put("tradeoffs", "A counter over random codes, because there are no collisions.");
        return sections;
    }
}
