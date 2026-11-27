package dev.mosiur.drill.progress.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import dev.mosiur.drill.problem.domain.Topic;
import dev.mosiur.drill.progress.model.ScoredDrill;
import dev.mosiur.drill.progress.model.SkillTrend;
import dev.mosiur.drill.progress.model.Streak;
import dev.mosiur.drill.progress.model.TopicStrength;
import dev.mosiur.drill.progress.model.WeekDay;
import org.junit.jupiter.api.Test;

class ProgressCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 27);

    @Test
    void streakCountsBackFromTodayOrYesterday() {
        Set<LocalDate> days = Set.of(TODAY.minusDays(1), TODAY.minusDays(2), TODAY.minusDays(3), TODAY.minusDays(6));

        assertThat(ProgressCalculator.streak(days, TODAY)).isEqualTo(new Streak(3, 3));
        assertThat(ProgressCalculator.streak(Set.of(TODAY, TODAY.minusDays(1)), TODAY).current()).isEqualTo(2);
        assertThat(ProgressCalculator.streak(Set.of(TODAY.minusDays(2)), TODAY).current()).isZero();
        assertThat(ProgressCalculator.streak(Set.of(), TODAY)).isEqualTo(new Streak(0, 0));
    }

    @Test
    void longestStreakLooksAtAllHistory() {
        Set<LocalDate> days = Set.of(TODAY.minusDays(20), TODAY.minusDays(19), TODAY.minusDays(18),
                TODAY.minusDays(17), TODAY);

        assertThat(ProgressCalculator.streak(days, TODAY)).isEqualTo(new Streak(1, 4));
    }

    @Test
    void skillsAverageTheLastTenAndCompareWithTheTenBefore() {
        List<ScoredDrill> drills = IntStream.range(0, 20)
                .mapToObj(i -> drill(i, 60, Map.of("api", i < 10 ? 2 : 3, "estimation", i < 10 ? 3 : 1)))
                .toList();

        List<SkillTrend> skills = ProgressCalculator.skills(drills);

        assertThat(skills).extracting(SkillTrend::title).containsExactly("API", "Estimation");
        assertThat(skills.get(0).average()).isEqualTo(3.0);
        assertThat(skills.get(0).deltaText()).isEqualTo("+1.0");
        assertThat(skills.get(1).deltaText()).isEqualTo("−2.0");
        assertThat(skills.get(1).falling()).isTrue();
    }

    @Test
    void firstDrillsHaveNoTrendYet() {
        SkillTrend api = ProgressCalculator.skills(List.of(drill(0, 50, Map.of("api", 2)))).getFirst();

        assertThat(api.delta()).isNull();
        assertThat(api.deltaText()).isEmpty();
        assertThat(api.points()).matches("0\\.0,\\d+\\.\\d 160\\.0,\\d+\\.\\d");
    }

    @Test
    void sparklineUsesDotsAndStaysInsideTheBox() {
        String points = ProgressCalculator.sparkline(List.of(0, 4, 4, 4, 0, 2));

        assertThat(points.split(" ")).hasSize(6).allSatisfy(point -> {
            String[] xy = point.split(",");
            assertThat(Double.parseDouble(xy[0])).isBetween(0.0, 160.0);
            assertThat(Double.parseDouble(xy[1])).isBetween(2.0, 30.0);
        });
    }

    @Test
    void untriedTopicsComeFirstThenTheWeakest() {
        List<ScoredDrill> drills = List.of(
                drill(0, 80, Map.of(), Topic.CACHING),
                drill(1, 40, Map.of(), Topic.PAYMENTS),
                drill(2, 60, Map.of(), Topic.CACHING));

        List<TopicStrength> topics = ProgressCalculator.topics(drills,
                EnumSet.of(Topic.CACHING, Topic.PAYMENTS, Topic.GEO));

        assertThat(topics).extracting(TopicStrength::name).containsExactly("geo", "payments", "caching");
        assertThat(topics.get(2)).isEqualTo(new TopicStrength("caching", 2, 70));
        assertThat(topics.get(0).tried()).isFalse();
    }

    @Test
    void weekShowsSevenDaysEndingToday() {
        List<ScoredDrill> drills = List.of(drill(-2, 71.6, Map.of()));

        List<WeekDay> week = ProgressCalculator.week(drills, Set.of(TODAY.minusDays(2), TODAY.minusDays(1)), TODAY);

        assertThat(week).hasSize(7);
        assertThat(week.getFirst().day()).isEqualTo(TODAY.minusDays(6));
        assertThat(week.get(4).score()).isEqualTo(72);
        assertThat(week.get(5).pending()).isTrue();
        assertThat(week.get(6).today()).isTrue();
        assertThat(week.get(6).submitted()).isFalse();
    }

    private static ScoredDrill drill(int dayOffset, double score, Map<String, Integer> sections, Topic... topics) {
        return new ScoredDrill(dayOffset + 100, TODAY.plusDays(dayOffset), false, 1, "Problem",
                BigDecimal.valueOf(score), Set.of(topics), sections);
    }
}
