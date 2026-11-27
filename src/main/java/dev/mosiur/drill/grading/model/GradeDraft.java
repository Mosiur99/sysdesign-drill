package dev.mosiur.drill.grading.model;

import java.util.List;
import java.util.Map;

import dev.mosiur.drill.grading.domain.FollowUp;
import dev.mosiur.drill.problem.domain.Dimension;

public record GradeDraft(String summary, List<ScoreDraft> scores, Map<String, Boolean> keyPoints,
                         List<String> strengths, List<String> improvements, List<FollowUp> followUps) {

    public record ScoreDraft(Dimension dimension, int score, String evidence, boolean verified, List<String> missing) {
    }

    public List<ScoreDraft> unverified() {
        return scores.stream().filter(score -> !score.verified()).toList();
    }
}
