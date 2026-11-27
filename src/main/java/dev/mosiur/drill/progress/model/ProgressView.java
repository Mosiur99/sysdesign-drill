package dev.mosiur.drill.progress.model;

import java.time.LocalDate;
import java.util.List;

public record ProgressView(int drillCount, LocalDate since, Integer recentAverage, Integer earlyAverage,
                           Streak streak, List<SkillTrend> skills, List<TopicStrength> topics,
                           List<RecentDrill> recent) {

    public static final int MIN_DRILLS_FOR_TRENDS = 3;

    public boolean hasTrends() {
        return drillCount >= MIN_DRILLS_FOR_TRENDS;
    }
}
