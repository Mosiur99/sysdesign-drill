package dev.mosiur.drill.drill.model;

import java.util.Set;

import dev.mosiur.drill.problem.domain.Topic;

public record Candidate(long problemId, int difficulty, Set<Topic> topics, Attempt attempt) {

    public static Candidate fresh(long problemId, int difficulty, Set<Topic> topics) {
        return new Candidate(problemId, difficulty, topics, null);
    }

    public boolean isFresh() {
        return attempt == null;
    }
}
