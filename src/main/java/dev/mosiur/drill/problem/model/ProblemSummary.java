package dev.mosiur.drill.problem.model;

import java.util.List;

import dev.mosiur.drill.problem.domain.Problem;

public record ProblemSummary(String slug, String title, int difficulty, List<String> topics) {

    public static ProblemSummary from(Problem problem) {
        List<String> topics = problem.getTopics().stream().sorted().map(topic -> topic.code()).toList();
        return new ProblemSummary(problem.getSlug(), problem.getTitle(), problem.getDifficulty(), topics);
    }
}
