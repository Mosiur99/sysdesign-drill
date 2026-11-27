package dev.mosiur.drill.progress.service.impl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import dev.mosiur.drill.answer.model.SectionGuide;
import dev.mosiur.drill.problem.domain.Topic;
import dev.mosiur.drill.progress.model.ScoredDrill;
import dev.mosiur.drill.progress.model.SkillTrend;
import dev.mosiur.drill.progress.model.Streak;
import dev.mosiur.drill.progress.model.TopicStrength;
import dev.mosiur.drill.progress.model.WeekDay;

final class ProgressCalculator {

    static final int WINDOW = 10;
    static final int SPARKLINE_POINTS = 20;
    static final int SPARKLINE_WIDTH = 160;
    static final int SPARKLINE_HEIGHT = 32;

    private ProgressCalculator() {
    }

    static Streak streak(Collection<LocalDate> submittedDays, LocalDate today) {
        TreeSet<LocalDate> days = new TreeSet<>(submittedDays);
        LocalDate day = days.contains(today) ? today : today.minusDays(1);
        int current = 0;
        while (days.contains(day)) {
            current++;
            day = day.minusDays(1);
        }
        int longest = 0;
        int run = 0;
        LocalDate previous = null;
        for (LocalDate submitted : days) {
            run = previous != null && previous.plusDays(1).equals(submitted) ? run + 1 : 1;
            longest = Math.max(longest, run);
            previous = submitted;
        }
        return new Streak(current, longest);
    }

    static List<SkillTrend> skills(List<ScoredDrill> drills) {
        List<SkillTrend> trends = new ArrayList<>();
        for (SectionGuide guide : SectionGuide.values()) {
            List<Integer> series = drills.stream()
                    .map(drill -> drill.sections().get(guide.key()))
                    .filter(Objects::nonNull)
                    .toList();
            if (series.isEmpty()) {
                continue;
            }
            List<Integer> recent = last(series, WINDOW);
            List<Integer> before = series.subList(Math.max(0, series.size() - 2 * WINDOW),
                    series.size() - recent.size());
            Double delta = before.isEmpty() ? null : average(recent) - average(before);
            trends.add(new SkillTrend(guide.title(), average(recent), delta, sparkline(series)));
        }
        trends.sort(Comparator.comparingDouble(SkillTrend::average).reversed());
        return trends;
    }

    static List<TopicStrength> topics(List<ScoredDrill> drills, Set<Topic> activeTopics) {
        List<TopicStrength> topics = new ArrayList<>();
        for (Topic topic : activeTopics) {
            List<ScoredDrill> inTopic = drills.stream().filter(drill -> drill.topics().contains(topic)).toList();
            Integer average = inTopic.isEmpty() ? null
                    : (int) Math.round(inTopic.stream().mapToDouble(ScoredDrill::scoreValue).average().orElseThrow());
            topics.add(new TopicStrength(topic.code(), inTopic.size(), average));
        }
        topics.sort(Comparator.comparing(TopicStrength::tried)
                .thenComparing(topic -> topic.average() == null ? 0 : topic.average()));
        return topics;
    }

    static List<WeekDay> week(List<ScoredDrill> drills, Set<LocalDate> submittedDays, LocalDate today) {
        Map<LocalDate, Integer> scores = drills.stream().collect(Collectors.toMap(ScoredDrill::day,
                drill -> (int) Math.round(drill.scoreValue()), (a, b) -> b));
        List<WeekDay> week = new ArrayList<>();
        for (LocalDate day = today.minusDays(6); !day.isAfter(today); day = day.plusDays(1)) {
            week.add(new WeekDay(day, day.equals(today), scores.get(day), submittedDays.contains(day)));
        }
        return week;
    }

    static Integer averageScore(List<ScoredDrill> drills) {
        return drills.isEmpty() ? null
                : (int) Math.round(drills.stream().mapToDouble(ScoredDrill::scoreValue).average().orElseThrow());
    }

    static String sparkline(List<Integer> series) {
        List<Integer> points = last(series, SPARKLINE_POINTS);
        List<Double> smoothed = new ArrayList<>();
        for (int i = 0; i < points.size(); i++) {
            smoothed.add(average(points.subList(Math.max(0, i - 2), i + 1)));
        }
        if (smoothed.size() == 1) {
            smoothed.add(smoothed.getFirst());
        }
        double step = (double) SPARKLINE_WIDTH / (smoothed.size() - 1);
        List<String> coordinates = new ArrayList<>();
        for (int i = 0; i < smoothed.size(); i++) {
            double y = SPARKLINE_HEIGHT - 2 - smoothed.get(i) / 4 * (SPARKLINE_HEIGHT - 4);
            coordinates.add(String.format(Locale.ROOT, "%.1f,%.1f", i * step, y));
        }
        return String.join(" ", coordinates);
    }

    private static <T> List<T> last(List<T> values, int count) {
        return values.subList(Math.max(0, values.size() - count), values.size());
    }

    private static double average(List<Integer> values) {
        return values.stream().mapToInt(Integer::intValue).average().orElse(0);
    }
}
