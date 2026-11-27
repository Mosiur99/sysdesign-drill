package dev.mosiur.drill.grading.service.impl;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import dev.mosiur.drill.grading.domain.FollowUp;
import dev.mosiur.drill.grading.model.ChecklistPoint;
import dev.mosiur.drill.grading.model.GradeDraft;
import dev.mosiur.drill.grading.model.GradeDraft.ScoreDraft;
import dev.mosiur.drill.grading.model.GradingInput;
import dev.mosiur.drill.grading.model.Rubric;
import dev.mosiur.drill.grading.service.InvalidGradeException;
import dev.mosiur.drill.problem.domain.Dimension;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class GradeValidator {

    private static final int MAX_LIST = 3;
    private static final int FOLLOW_UPS = 2;
    private static final Pattern FRAGMENT_BREAK = Pattern.compile("\\.\\.\\.|…");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern SECTION_HEADING = Pattern.compile("(?m)^\\s*##\\s*[a-z_]+\\s*$");

    public GradeDraft validate(JsonNode json, GradingInput input) {
        Map<Dimension, ScoreDraft> scores = scores(json.path("dimensions"), input);
        List<Dimension> absent = Rubric.CRITERIA.stream().map(Rubric.Criterion::dimension)
                .filter(dimension -> !scores.containsKey(dimension)).toList();
        if (!absent.isEmpty()) {
            throw new InvalidGradeException("no score for " + absent.stream().map(Dimension::code)
                    .collect(Collectors.joining(", ")) + ".");
        }
        List<String> improvements = strings(json.path("improvements"), MAX_LIST);
        if (improvements.isEmpty()) {
            throw new InvalidGradeException("\"improvements\" is empty.");
        }
        return new GradeDraft(json.path("summary").asString("").strip(), List.copyOf(scores.values()),
                keyPoints(json.path("key_points"), input.checklist()), strings(json.path("strengths"), MAX_LIST),
                improvements, followUps(json.path("follow_ups")));
    }

    private Map<Dimension, ScoreDraft> scores(JsonNode items, GradingInput input) {
        String answer = normalise(input.answerText());
        Map<Dimension, ScoreDraft> scores = new EnumMap<>(Dimension.class);
        for (JsonNode item : items) {
            Dimension dimension;
            try {
                dimension = Dimension.fromCode(item.path("dimension").asString(""));
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (scores.containsKey(dimension)) {
                continue;
            }
            String section = input.sections().getOrDefault(dimension.code(), "");
            int score = Math.clamp(item.path("score").asInt(0), 0, Rubric.MAX_SCORE);
            String evidence = item.path("evidence").asString("").strip();
            List<String> missing = strings(item.path("missing"), MAX_LIST);
            if (section.isBlank() && evidence.isEmpty()) {
                score = 0;
            }
            boolean verified = evidence.isEmpty() ? score == 0 : isQuoted(evidence, answer);
            scores.put(dimension, new ScoreDraft(dimension, score, evidence, verified, missing));
        }
        return scores;
    }

    static boolean isQuoted(String evidence, String normalisedAnswer) {
        String withoutHeadings = SECTION_HEADING.matcher(evidence).replaceAll(" ");
        List<String> fragments = FRAGMENT_BREAK.splitAsStream(withoutHeadings)
                .map(GradeValidator::normalise)
                .filter(fragment -> !fragment.isEmpty())
                .toList();
        return !fragments.isEmpty() && fragments.stream().allMatch(normalisedAnswer::contains);
    }

    static String normalise(String text) {
        String plain = text.toLowerCase()
                .replace('‘', '\'').replace('’', '\'')
                .replace('“', '"').replace('”', '"')
                .replace('–', '-').replace('—', '-');
        String collapsed = WHITESPACE.matcher(plain).replaceAll(" ").strip();
        return collapsed.replaceAll("^[\"']+|[\"']+$", "").strip();
    }

    private static Map<String, Boolean> keyPoints(JsonNode items, List<ChecklistPoint> checklist) {
        Set<String> known = checklist.stream().map(ChecklistPoint::code).collect(Collectors.toSet());
        Map<String, Boolean> covered = new LinkedHashMap<>();
        checklist.forEach(point -> covered.put(point.code(), false));
        for (JsonNode item : items) {
            String code = item.path("code").asString("");
            if (known.contains(code) && item.path("covered").asBoolean(false)) {
                covered.put(code, true);
            }
        }
        return covered;
    }

    private static List<FollowUp> followUps(JsonNode items) {
        List<FollowUp> followUps = new ArrayList<>();
        for (JsonNode item : items) {
            String question = item.path("question").asString("").strip();
            String dimension = item.path("dimension").asString("");
            if (!question.isEmpty() && followUps.size() < FOLLOW_UPS) {
                try {
                    followUps.add(new FollowUp(Dimension.fromCode(dimension).code(), question));
                } catch (IllegalArgumentException ignored) {
                    followUps.add(new FollowUp(null, question));
                }
            }
        }
        return followUps;
    }

    private static List<String> strings(JsonNode items, int limit) {
        List<String> values = new ArrayList<>();
        for (JsonNode item : items) {
            String value = item.asString("").strip();
            if (!value.isEmpty() && values.size() < limit) {
                values.add(value);
            }
        }
        return values;
    }
}
