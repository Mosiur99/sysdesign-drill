package dev.mosiur.drill.progress.model;

import java.util.List;

public record TodaySummary(List<WeekDay> week, SkillTrend weakestSkill, TopicStrength weakestTopic,
                           NextRetry nextRetry) {

    public boolean hasFocus() {
        return weakestSkill != null || weakestTopic != null || nextRetry != null;
    }
}
