package dev.mosiur.drill.progress.model;

import java.util.List;

public record WeeklyReport(int drills, Integer average, Streak streak, List<SkillTrend> sections,
                           TopicStrength strongest, TopicStrength weakest, List<NextRetry> upcomingRetries) {
}
