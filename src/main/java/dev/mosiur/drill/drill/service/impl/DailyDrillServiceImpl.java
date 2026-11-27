package dev.mosiur.drill.drill.service.impl;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.mosiur.drill.drill.model.Attempt;
import dev.mosiur.drill.drill.model.Candidate;
import dev.mosiur.drill.drill.model.TodayDrill;
import dev.mosiur.drill.drill.repository.DrillRepository;
import dev.mosiur.drill.drill.service.DailyDrillService;
import dev.mosiur.drill.drill.service.DailyPicker;
import dev.mosiur.drill.problem.domain.Problem;
import dev.mosiur.drill.problem.repository.ProblemRepository;
import dev.mosiur.drill.progress.domain.ProblemProgress;
import dev.mosiur.drill.progress.repository.ProblemProgressRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailyDrillServiceImpl implements DailyDrillService {

    private final DrillRepository drills;
    private final ProblemRepository problems;
    private final ProblemProgressRepository progress;
    private final DailyPicker picker;
    private final Clock clock;

    public DailyDrillServiceImpl(DrillRepository drills, ProblemRepository problems,
                                 ProblemProgressRepository progress, DailyPicker picker, Clock clock) {
        this.drills = drills;
        this.problems = problems;
        this.progress = progress;
        this.picker = picker;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Optional<TodayDrill> assignToday() {
        LocalDate today = LocalDate.now(clock);
        if (drills.findByScheduledFor(today).isEmpty()) {
            picker.pick(candidates(), today).ifPresent(pick ->
                    drills.insertIfAbsent(pick.problemId(), today, pick.kind().name()));
        }
        return drills.findByScheduledFor(today).map(TodayDrill::from);
    }

    private List<Candidate> candidates() {
        Map<Long, ProblemProgress> attempts = progress.findAttempted().stream()
                .collect(Collectors.toMap(ProblemProgress::getProblemId, Function.identity()));
        return problems.findByActiveTrue().stream()
                .map(problem -> toCandidate(problem, attempts.get(problem.getId())))
                .toList();
    }

    private Candidate toCandidate(Problem problem, ProblemProgress attempt) {
        if (attempt == null) {
            return Candidate.fresh(problem.getId(), problem.getDifficulty(), problem.getTopics());
        }
        return new Candidate(problem.getId(), problem.getDifficulty(), problem.getTopics(), new Attempt(
                attempt.getLastScore().doubleValue(), attempt.getLastAttemptedOn(), attempt.getNextDue(),
                attempt.isRetired()));
    }
}
