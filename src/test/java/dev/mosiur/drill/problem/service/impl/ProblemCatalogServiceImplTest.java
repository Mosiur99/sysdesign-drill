package dev.mosiur.drill.problem.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import dev.mosiur.drill.problem.domain.Dimension;
import dev.mosiur.drill.problem.domain.KeyPoint;
import dev.mosiur.drill.problem.domain.Problem;
import dev.mosiur.drill.problem.domain.Topic;
import dev.mosiur.drill.problem.model.CatalogSyncResult;
import dev.mosiur.drill.problem.model.KeyPointDefinition;
import dev.mosiur.drill.problem.model.ProblemDefinition;
import dev.mosiur.drill.problem.model.ProblemSummary;
import dev.mosiur.drill.problem.repository.ProblemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProblemCatalogServiceImplTest {

    @Mock
    private ProblemRepository problems;

    @InjectMocks
    private ProblemCatalogServiceImpl catalog;

    @Test
    void createsNewProblemsUpdatesExistingOnesAndDeactivatesMissingOnes() {
        Problem cache = problem("cache", "Old cache title", "old-point");
        Problem retired = problem("retired", "Retired", "x");
        when(problems.findAll()).thenReturn(List.of(cache, retired));
        when(problems.save(any(Problem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CatalogSyncResult result = catalog.sync(List.of(
                definition("cache", "New cache title", "eviction"),
                definition("queue", "Queue", "ordering")));

        assertThat(result).isEqualTo(new CatalogSyncResult(1, 1, 1));
        assertThat(cache.getTitle()).isEqualTo("New cache title");
        assertThat(cache.activeKeyPoints()).extracting(KeyPoint::getCode).containsExactly("eviction");
        assertThat(retired.isActive()).isFalse();
        ArgumentCaptor<Problem> saved = ArgumentCaptor.forClass(Problem.class);
        verify(problems, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Problem::getSlug).containsExactly("cache", "queue");
    }

    @Test
    void stripsWhitespaceFromYamlBlockText() {
        when(problems.findAll()).thenReturn(List.of());
        when(problems.save(any(Problem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        ProblemDefinition definition = new ProblemDefinition("cache", "Cache", 1, Set.of(Topic.CACHING),
                "Design a cache.\n", List.of(new KeyPointDefinition("eviction", Dimension.DEEP_DIVE, " LRU \n")));

        catalog.sync(List.of(definition));

        ArgumentCaptor<Problem> saved = ArgumentCaptor.forClass(Problem.class);
        verify(problems).save(saved.capture());
        assertThat(saved.getValue().getPrompt()).isEqualTo("Design a cache.");
        assertThat(saved.getValue().activeKeyPoints().getFirst().getText()).isEqualTo("LRU");
    }

    @Test
    void doesNotCountAnAlreadyInactiveProblemAsDeactivatedAgain() {
        Problem retired = problem("retired", "Retired", "x");
        retired.deactivate();
        when(problems.findAll()).thenReturn(List.of(retired));

        assertThat(catalog.sync(List.of())).isEqualTo(new CatalogSyncResult(0, 0, 0));
    }

    @Test
    void listsActiveProblemsAsSummariesWithSortedTopicCodes() {
        Problem problem = new Problem("chat");
        problem.update("Chat", "prompt", 2, Set.of(Topic.STORAGE, Topic.REAL_TIME, Topic.MESSAGING));
        when(problems.findByActiveTrue()).thenReturn(List.of(problem));

        assertThat(catalog.listActive()).containsExactly(
                new ProblemSummary("chat", "Chat", 2, List.of("storage", "messaging", "real-time")));
    }

    @Test
    void listsByDifficultyThenTitleIgnoringCase() {
        when(problems.findByActiveTrue()).thenReturn(List.of(
                titled("Design a URL shortener", 1),
                titled("Design a chat", 2),
                titled("Design a distributed rate limiter", 1)));

        assertThat(catalog.listActive()).extracting(ProblemSummary::title).containsExactly(
                "Design a distributed rate limiter", "Design a URL shortener", "Design a chat");
    }

    private static Problem titled(String title, int difficulty) {
        Problem problem = new Problem(title.toLowerCase().replace(' ', '-'));
        problem.update(title, "prompt", difficulty, Set.of(Topic.CACHING));
        return problem;
    }

    private static Problem problem(String slug, String title, String keyPoint) {
        Problem problem = new Problem(slug);
        problem.update(title, "prompt", 1, Set.of(Topic.CACHING));
        problem.putKeyPoint(keyPoint, Dimension.API, "text");
        return problem;
    }

    private static ProblemDefinition definition(String slug, String title, String keyPoint) {
        return new ProblemDefinition(slug, title, 1, Set.of(Topic.CACHING), "prompt",
                List.of(new KeyPointDefinition(keyPoint, Dimension.API, "text")));
    }
}
