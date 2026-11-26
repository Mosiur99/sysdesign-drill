package dev.mosiur.drill.problem.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.Set;

import org.junit.jupiter.api.Test;

class ProblemTest {

    @Test
    void updateReplacesFieldsAndTopics() {
        Problem problem = new Problem("cache");
        problem.update("Old", "old prompt", 1, Set.of(Topic.CACHING, Topic.STORAGE));

        problem.update("New", "new prompt", 3, Set.of(Topic.MESSAGING));

        assertThat(problem.getTitle()).isEqualTo("New");
        assertThat(problem.getPrompt()).isEqualTo("new prompt");
        assertThat(problem.getDifficulty()).isEqualTo(3);
        assertThat(problem.getTopics()).containsExactly(Topic.MESSAGING);
    }

    @Test
    void putKeyPointAddsNewCodesAndUpdatesExistingOnes() {
        Problem problem = new Problem("cache");
        problem.putKeyPoint("eviction", Dimension.DEEP_DIVE, "LRU");

        problem.putKeyPoint("eviction", Dimension.TRADEOFFS, "LRU vs LFU");
        problem.putKeyPoint("sizing", Dimension.ESTIMATION, "Memory");

        assertThat(problem.activeKeyPoints())
                .extracting(KeyPoint::getCode, KeyPoint::getDimension, KeyPoint::getText)
                .containsExactly(
                        tuple("eviction", Dimension.TRADEOFFS, "LRU vs LFU"),
                        tuple("sizing", Dimension.ESTIMATION, "Memory"));
    }

    @Test
    void removedKeyPointsAreDeactivatedAndComeBackWhenReAdded() {
        Problem problem = new Problem("cache");
        problem.putKeyPoint("eviction", Dimension.DEEP_DIVE, "LRU");
        problem.putKeyPoint("sizing", Dimension.ESTIMATION, "Memory");

        problem.retainKeyPoints(Set.of("sizing"));
        assertThat(problem.activeKeyPoints()).extracting(KeyPoint::getCode).containsExactly("sizing");

        problem.putKeyPoint("eviction", Dimension.DEEP_DIVE, "LRU");
        assertThat(problem.activeKeyPoints()).extracting(KeyPoint::getCode).containsExactly("eviction", "sizing");
    }

    @Test
    void updateReactivatesADeactivatedProblem() {
        Problem problem = new Problem("cache");
        problem.deactivate();

        problem.update("Cache", "prompt", 1, Set.of(Topic.CACHING));

        assertThat(problem.isActive()).isTrue();
    }
}
