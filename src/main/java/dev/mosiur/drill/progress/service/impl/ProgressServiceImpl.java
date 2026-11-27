package dev.mosiur.drill.progress.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

import dev.mosiur.drill.progress.model.ProgressView;
import dev.mosiur.drill.progress.model.RecentDrill;
import dev.mosiur.drill.progress.model.ScoredDrill;
import dev.mosiur.drill.progress.model.SkillTrend;
import dev.mosiur.drill.progress.model.Streak;
import dev.mosiur.drill.progress.model.TodaySummary;
import dev.mosiur.drill.progress.model.TopicStrength;
import dev.mosiur.drill.progress.repository.ProgressQueries;
import dev.mosiur.drill.progress.service.ProgressService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProgressServiceImpl implements ProgressService {

    static final int RECENT_DRILLS = 10;

    private final ProgressQueries queries;
    private final Clock clock;

    public ProgressServiceImpl(ProgressQueries queries, Clock clock) {
        this.queries = queries;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public ProgressView progress() {
        List<ScoredDrill> drills = queries.scoredDrills();
        int count = drills.size();
        int window = ProgressCalculator.WINDOW;
        Integer recent = ProgressCalculator.averageScore(drills.subList(Math.max(0, count - window), count));
        Integer early = count > window ? ProgressCalculator.averageScore(drills.subList(0, window)) : null;
        List<RecentDrill> history = drills.reversed().stream()
                .limit(RECENT_DRILLS)
                .map(drill -> new RecentDrill(drill.drillId(), drill.day(), drill.title(), drill.retry(),
                        (int) Math.round(drill.scoreValue())))
                .toList();
        return new ProgressView(count, drills.isEmpty() ? null : drills.getFirst().day(), recent, early, streak(),
                ProgressCalculator.skills(drills), ProgressCalculator.topics(drills, queries.activeTopics()), history);
    }

    @Override
    @Transactional(readOnly = true)
    public TodaySummary today() {
        LocalDate today = LocalDate.now(clock);
        List<ScoredDrill> drills = queries.scoredDrills();
        SkillTrend weakestSkill = drills.size() < ProgressView.MIN_DRILLS_FOR_TRENDS ? null
                : ProgressCalculator.skills(drills).stream()
                        .min(Comparator.comparingDouble(SkillTrend::average)).orElse(null);
        TopicStrength weakestTopic = ProgressCalculator.topics(drills, queries.activeTopics()).stream()
                .findFirst().orElse(null);
        return new TodaySummary(ProgressCalculator.week(drills, queries.submittedDays(), today), weakestSkill,
                weakestTopic, queries.nextRetry().orElse(null));
    }

    @Override
    @Transactional(readOnly = true)
    public Streak streak() {
        return ProgressCalculator.streak(queries.submittedDays(), LocalDate.now(clock));
    }
}
