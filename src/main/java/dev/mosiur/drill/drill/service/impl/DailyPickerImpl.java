package dev.mosiur.drill.drill.service.impl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Random;
import java.util.Set;

import dev.mosiur.drill.drill.domain.DrillKind;
import dev.mosiur.drill.drill.model.Attempt;
import dev.mosiur.drill.drill.model.Candidate;
import dev.mosiur.drill.drill.model.Pick;
import dev.mosiur.drill.drill.service.DailyPicker;
import dev.mosiur.drill.problem.domain.Topic;
import org.springframework.stereotype.Component;

@Component
public class DailyPickerImpl implements DailyPicker {

    static final int STARTING_DIFFICULTY = 2;
    private static final int RECENT_ATTEMPTS = 5;
    private static final double STEP_UP_AVERAGE = 75;
    private static final double STEP_DOWN_AVERAGE = 50;

    private static final Comparator<Candidate> MOST_OVERDUE = Comparator
            .comparing((Candidate candidate) -> candidate.attempt().nextDue())
            .thenComparingDouble(candidate -> candidate.attempt().lastScore())
            .thenComparingLong(Candidate::problemId);

    private static final Comparator<Candidate> EARLIEST_RETRY = Comparator
            .comparing((Candidate candidate) -> candidate.attempt().retired())
            .thenComparing(candidate -> candidate.attempt().nextDue(), Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(candidate -> candidate.attempt().lastAttemptedOn())
            .thenComparingLong(Candidate::problemId);

    private static final Comparator<Candidate> MOST_RECENT_FIRST = Comparator
            .comparing((Candidate candidate) -> candidate.attempt().lastAttemptedOn())
            .reversed();

    @Override
    public Optional<Pick> pick(List<Candidate> candidates, LocalDate day) {
        List<Candidate> attempted = candidates.stream().filter(candidate -> !candidate.isFresh()).toList();
        Random random = new Random(day.toEpochDay());
        return dueRetry(attempted, day)
                .or(() -> freshProblem(candidates, attempted, random))
                .or(() -> earliestRetry(attempted));
    }

    private Optional<Pick> dueRetry(List<Candidate> attempted, LocalDate day) {
        return attempted.stream()
                .filter(candidate -> candidate.attempt().isDueBy(day))
                .min(MOST_OVERDUE)
                .map(candidate -> new Pick(candidate.problemId(), DrillKind.RETRY));
    }

    private Optional<Pick> freshProblem(List<Candidate> candidates, List<Candidate> attempted, Random random) {
        List<Candidate> fresh = candidates.stream().filter(Candidate::isFresh).toList();
        int target = targetDifficulty(attempted);
        for (Topic topic : weakestTopicsFirst(candidates, attempted, random)) {
            List<Candidate> inTopic = fresh.stream().filter(candidate -> candidate.topics().contains(topic)).toList();
            if (!inTopic.isEmpty()) {
                List<Candidate> closest = closestDifficulty(inTopic, target);
                Candidate chosen = closest.get(random.nextInt(closest.size()));
                return Optional.of(new Pick(chosen.problemId(), DrillKind.NEW));
            }
        }
        return Optional.empty();
    }

    private Optional<Pick> earliestRetry(List<Candidate> attempted) {
        return attempted.stream()
                .min(EARLIEST_RETRY)
                .map(candidate -> new Pick(candidate.problemId(), DrillKind.RETRY));
    }

    int targetDifficulty(List<Candidate> attempted) {
        List<Candidate> recent = attempted.stream().sorted(MOST_RECENT_FIRST).limit(RECENT_ATTEMPTS).toList();
        if (recent.isEmpty()) {
            return STARTING_DIFFICULTY;
        }
        int base = recent.getFirst().difficulty();
        double average = recent.stream().mapToDouble(candidate -> candidate.attempt().lastScore()).average().orElseThrow();
        if (average >= STEP_UP_AVERAGE) {
            return Math.min(3, base + 1);
        }
        if (average < STEP_DOWN_AVERAGE) {
            return Math.max(1, base - 1);
        }
        return base;
    }

    List<Topic> weakestTopicsFirst(List<Candidate> candidates, List<Candidate> attempted, Random random) {
        Set<Topic> present = EnumSet.noneOf(Topic.class);
        candidates.forEach(candidate -> present.addAll(candidate.topics()));
        List<Topic> topics = new ArrayList<>(present);
        Collections.shuffle(topics, random);
        topics.sort(Comparator.comparingDouble(topic -> averageScore(attempted, topic).orElse(-1)));
        return topics;
    }

    private OptionalDouble averageScore(List<Candidate> attempted, Topic topic) {
        return attempted.stream()
                .filter(candidate -> candidate.topics().contains(topic))
                .map(Candidate::attempt)
                .mapToDouble(Attempt::lastScore)
                .average();
    }

    private List<Candidate> closestDifficulty(List<Candidate> inTopic, int target) {
        Comparator<Candidate> byCloseness = Comparator
                .comparingInt((Candidate candidate) -> Math.abs(candidate.difficulty() - target))
                .thenComparingInt(Candidate::difficulty);
        Candidate best = inTopic.stream().min(byCloseness).orElseThrow();
        return inTopic.stream()
                .filter(candidate -> candidate.difficulty() == best.difficulty())
                .sorted(Comparator.comparingLong(Candidate::problemId))
                .toList();
    }
}
