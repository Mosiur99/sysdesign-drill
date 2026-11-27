package dev.mosiur.drill.drill.service.impl;

import static dev.mosiur.drill.problem.domain.Topic.CACHING;
import static dev.mosiur.drill.problem.domain.Topic.GEO;
import static dev.mosiur.drill.problem.domain.Topic.MESSAGING;
import static dev.mosiur.drill.problem.domain.Topic.PAYMENTS;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;

import dev.mosiur.drill.drill.domain.DrillKind;
import dev.mosiur.drill.drill.model.Attempt;
import dev.mosiur.drill.drill.model.Candidate;
import dev.mosiur.drill.drill.model.Pick;
import dev.mosiur.drill.problem.domain.Topic;
import org.junit.jupiter.api.Test;

class DailyPickerImplTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 27);

    private final DailyPickerImpl picker = new DailyPickerImpl();

    @Test
    void picksNothingFromAnEmptyBank() {
        assertThat(picker.pick(List.of(), TODAY)).isEmpty();
    }

    @Test
    void dueRetryWinsOverFreshProblems() {
        List<Candidate> candidates = List.of(
                fresh(1, 2, GEO),
                attempted(2, 2, 40, TODAY.minusDays(3), TODAY, CACHING));

        assertThat(picker.pick(candidates, TODAY)).contains(new Pick(2, DrillKind.RETRY));
    }

    @Test
    void mostOverdueRetryComesFirstThenLowestScore() {
        List<Candidate> candidates = List.of(
                attempted(1, 2, 30, TODAY.minusDays(5), TODAY.minusDays(1), CACHING),
                attempted(2, 2, 60, TODAY.minusDays(9), TODAY.minusDays(2), CACHING),
                attempted(3, 2, 45, TODAY.minusDays(9), TODAY.minusDays(2), CACHING));

        assertThat(picker.pick(candidates, TODAY)).contains(new Pick(3, DrillKind.RETRY));
    }

    @Test
    void ignoresRetriesNotYetDueAndRetiredProblems() {
        List<Candidate> candidates = List.of(
                attempted(1, 2, 40, TODAY.minusDays(1), TODAY.plusDays(1), CACHING),
                new Candidate(2, 2, Set.of(CACHING), new Attempt(90, TODAY.minusDays(40), TODAY.minusDays(1), true)),
                fresh(3, 2, CACHING));

        assertThat(picker.pick(candidates, TODAY)).contains(new Pick(3, DrillKind.NEW));
    }

    @Test
    void untriedTopicComesBeforeTheWeakestTriedTopic() {
        List<Candidate> candidates = List.of(
                attempted(1, 2, 20, TODAY.minusDays(1), TODAY.plusDays(2), CACHING),
                fresh(2, 2, CACHING),
                fresh(3, 2, GEO));

        assertThat(picker.pick(candidates, TODAY)).contains(new Pick(3, DrillKind.NEW));
    }

    @Test
    void picksFromTheTriedTopicWithTheLowestAverage() {
        List<Candidate> candidates = List.of(
                attempted(1, 2, 80, TODAY.minusDays(2), TODAY.plusDays(20), CACHING),
                attempted(2, 2, 55, TODAY.minusDays(1), TODAY.plusDays(9), MESSAGING),
                fresh(3, 2, CACHING),
                fresh(4, 2, MESSAGING));

        assertThat(picker.pick(candidates, TODAY)).contains(new Pick(4, DrillKind.NEW));
    }

    @Test
    void widensToTheNextTopicWhenTheWeakestHasNoFreshProblems() {
        List<Candidate> candidates = List.of(
                attempted(1, 2, 30, TODAY.minusDays(1), TODAY.plusDays(2), PAYMENTS),
                attempted(2, 2, 70, TODAY.minusDays(2), TODAY.plusDays(8), CACHING),
                fresh(3, 2, CACHING));

        assertThat(picker.pick(candidates, TODAY)).contains(new Pick(3, DrillKind.NEW));
    }

    @Test
    void prefersTheTargetDifficultyThenTheClosestEasierOne() {
        List<Candidate> exact = List.of(fresh(1, 1, GEO), fresh(2, 2, GEO), fresh(3, 3, GEO));
        List<Candidate> noMedium = List.of(fresh(1, 1, GEO), fresh(3, 3, GEO));

        assertThat(picker.pick(exact, TODAY)).contains(new Pick(2, DrillKind.NEW));
        assertThat(picker.pick(noMedium, TODAY)).contains(new Pick(1, DrillKind.NEW));
    }

    @Test
    void startsAtMediumWithoutHistory() {
        assertThat(picker.targetDifficulty(List.of())).isEqualTo(DailyPickerImpl.STARTING_DIFFICULTY);
    }

    @Test
    void stepsDifficultyUpAfterStrongRecentScoresAndDownAfterWeakOnes() {
        assertThat(picker.targetDifficulty(recent(2, 80, 75, 90))).isEqualTo(3);
        assertThat(picker.targetDifficulty(recent(2, 40, 55, 30))).isEqualTo(1);
        assertThat(picker.targetDifficulty(recent(2, 60, 70, 50))).isEqualTo(2);
        assertThat(picker.targetDifficulty(recent(3, 95, 90))).isEqualTo(3);
        assertThat(picker.targetDifficulty(recent(1, 10, 20))).isEqualTo(1);
    }

    @Test
    void onlyTheLastFiveAttemptsCount() {
        List<Candidate> attempts = List.of(
                attempted(1, 2, 80, TODAY.minusDays(1), null, CACHING),
                attempted(2, 2, 80, TODAY.minusDays(2), null, CACHING),
                attempted(3, 2, 80, TODAY.minusDays(3), null, CACHING),
                attempted(4, 2, 80, TODAY.minusDays(4), null, CACHING),
                attempted(5, 2, 80, TODAY.minusDays(5), null, CACHING),
                attempted(6, 2, 0, TODAY.minusDays(6), null, CACHING));

        assertThat(picker.targetDifficulty(attempts)).isEqualTo(3);
    }

    @Test
    void ordersUntriedTopicsFirstThenByAverageScore() {
        List<Candidate> attempted = List.of(
                attempted(1, 2, 70, TODAY.minusDays(1), null, CACHING),
                attempted(2, 2, 40, TODAY.minusDays(2), null, MESSAGING));
        List<Candidate> candidates = List.of(attempted.get(0), attempted.get(1), fresh(3, 2, GEO));

        List<Topic> order = picker.weakestTopicsFirst(candidates, attempted, new Random(1));

        assertThat(order).containsExactly(GEO, MESSAGING, CACHING);
    }

    @Test
    void retriesTheEarliestDueProblemOnceEveryProblemWasTried() {
        List<Candidate> candidates = List.of(
                attempted(1, 2, 80, TODAY.minusDays(3), TODAY.plusDays(27), CACHING),
                attempted(2, 2, 60, TODAY.minusDays(2), TODAY.plusDays(8), CACHING),
                new Candidate(3, 2, Set.of(GEO), new Attempt(90, TODAY.minusDays(30), null, true)));

        assertThat(picker.pick(candidates, TODAY)).contains(new Pick(2, DrillKind.RETRY));
    }

    @Test
    void retriesTheLongestUnseenProblemWhenEverythingIsRetired() {
        List<Candidate> candidates = List.of(
                new Candidate(1, 2, Set.of(GEO), new Attempt(90, TODAY.minusDays(10), null, true)),
                new Candidate(2, 2, Set.of(GEO), new Attempt(85, TODAY.minusDays(50), null, true)));

        assertThat(picker.pick(candidates, TODAY)).contains(new Pick(2, DrillKind.RETRY));
    }

    @Test
    void sameDayGivesTheSamePickAndDaysVary() {
        List<Candidate> candidates = IntStream.rangeClosed(1, 12)
                .mapToObj(id -> fresh(id, 2, Topic.values()[id % 4]))
                .toList();

        Pick first = picker.pick(candidates, TODAY).orElseThrow();
        assertThat(picker.pick(candidates, TODAY)).contains(first);

        long distinct = IntStream.range(0, 30)
                .mapToObj(offset -> picker.pick(candidates, TODAY.plusDays(offset)).orElseThrow())
                .distinct()
                .count();
        assertThat(distinct).isGreaterThan(3);
    }

    private static Candidate fresh(long id, int difficulty, Topic topic) {
        return Candidate.fresh(id, difficulty, Set.of(topic));
    }

    private static Candidate attempted(long id, int difficulty, double score, LocalDate on, LocalDate nextDue,
                                       Topic topic) {
        return new Candidate(id, difficulty, Set.of(topic), new Attempt(score, on, nextDue, false));
    }

    private static List<Candidate> recent(int latestDifficulty, double... scores) {
        return IntStream.range(0, scores.length)
                .mapToObj(i -> attempted(i + 1, i == 0 ? latestDifficulty : 2, scores[i],
                        TODAY.minusDays(i + 1), null, CACHING))
                .toList();
    }
}
