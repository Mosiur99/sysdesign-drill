package dev.mosiur.drill.grading.service.impl;

import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import dev.mosiur.drill.answer.domain.Answer;
import dev.mosiur.drill.answer.model.AnswerSubmitted;
import dev.mosiur.drill.answer.model.SectionGuide;
import dev.mosiur.drill.answer.repository.AnswerRepository;
import dev.mosiur.drill.answer.service.DrillNotFoundException;
import dev.mosiur.drill.drill.domain.Drill;
import dev.mosiur.drill.drill.domain.DrillKind;
import dev.mosiur.drill.drill.domain.DrillStatus;
import dev.mosiur.drill.drill.repository.DrillRepository;
import dev.mosiur.drill.grading.domain.DimensionScore;
import dev.mosiur.drill.grading.domain.Grade;
import dev.mosiur.drill.grading.domain.Grade.Usage;
import dev.mosiur.drill.grading.domain.GradeStatus;
import dev.mosiur.drill.grading.domain.KeyPointHit;
import dev.mosiur.drill.grading.model.ChecklistPoint;
import dev.mosiur.drill.grading.model.GradeDraft;
import dev.mosiur.drill.grading.model.GradeDraft.ScoreDraft;
import dev.mosiur.drill.grading.model.GradingInput;
import dev.mosiur.drill.grading.model.ResultView;
import dev.mosiur.drill.grading.model.ResultView.ChecklistItem;
import dev.mosiur.drill.grading.model.ResultView.DimensionView;
import dev.mosiur.drill.grading.model.ResultView.State;
import dev.mosiur.drill.grading.model.Rubric;
import dev.mosiur.drill.grading.repository.GradeRepository;
import dev.mosiur.drill.grading.service.GradingService;
import dev.mosiur.drill.grading.service.InvalidGradeException;
import dev.mosiur.drill.grading.service.RegradeNotAllowedException;
import dev.mosiur.drill.llm.model.LlmResult;
import dev.mosiur.drill.llm.service.LlmClient;
import dev.mosiur.drill.llm.service.LlmException;
import dev.mosiur.drill.problem.domain.Dimension;
import dev.mosiur.drill.problem.domain.KeyPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class GradingServiceImpl implements GradingService {

    static final int MAX_UNVERIFIED_QUOTES = 2;
    static final Duration STUCK_AFTER = Duration.ofMinutes(2);
    private static final String INVALID_RESPONSE = "INVALID_RESPONSE";
    private static final Logger log = LoggerFactory.getLogger(GradingServiceImpl.class);

    private final DrillRepository drills;
    private final AnswerRepository answers;
    private final GradeRepository grades;
    private final LlmClient llm;
    private final GradingPrompt prompt;
    private final GradeValidator validator;
    private final TransactionTemplate transaction;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public GradingServiceImpl(DrillRepository drills, AnswerRepository answers, GradeRepository grades, LlmClient llm,
                              GradingPrompt prompt, GradeValidator validator, TransactionTemplate transaction,
                              ApplicationEventPublisher events, Clock clock) {
        this.drills = drills;
        this.answers = answers;
        this.grades = grades;
        this.llm = llm;
        this.prompt = prompt;
        this.validator = validator;
        this.transaction = transaction;
        this.events = events;
        this.clock = clock;
    }

    @Override
    public void grade(long drillId) {
        GradingInput input = transaction.execute(status -> load(drillId));
        if (input == null) {
            return;
        }
        Grade grade = evaluate(input);
        transaction.executeWithoutResult(status -> save(drillId, grade));
        log.info("Drill {} graded: {} overall={} unverified_quotes={} attempts={}", drillId, grade.getStatus(),
                grade.getOverall(), grade.getUnverifiedQuotes(), grade.getAttempts());
    }

    @Override
    @Transactional
    public void regrade(long drillId) {
        Drill drill = drills.findByIdForUpdate(drillId).orElseThrow(() -> new DrillNotFoundException(drillId));
        Answer answer = mainAnswer(drillId).orElseThrow(() -> new RegradeNotAllowedException(drillId));
        if (drill.getStatus() != DrillStatus.GRADING && drill.getStatus() != DrillStatus.GRADED) {
            throw new RegradeNotAllowedException(drillId);
        }
        grades.findByAnswerId(answer.getId()).ifPresent(grades::delete);
        grades.flush();
        drill.regrade();
        events.publishEvent(new AnswerSubmitted(drillId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> stuckDrills() {
        return drills.findUngradedSubmittedBefore(clock.instant().minus(STUCK_AFTER));
    }

    @Override
    @Transactional(readOnly = true)
    public ResultView result(long drillId) {
        Drill drill = drills.findById(drillId).orElseThrow(() -> new DrillNotFoundException(drillId));
        Optional<Answer> answer = mainAnswer(drillId);
        Optional<Grade> grade = answer.flatMap(found -> grades.findByAnswerId(found.getId()));
        State state = state(drill, grade);
        Integer minutes = answer.map(Answer::getSecondsSpent).map(seconds -> Math.round(seconds / 60f)).orElse(null);
        int words = answer.map(Answer::getWordCount).orElse(0);
        String title = drill.getProblem().getTitle();
        boolean retry = drill.getKind() == DrillKind.RETRY;
        if (state != State.GRADED) {
            String failure = state == State.FAILED ? failureMessage(grade.orElseThrow().getFailureReason()) : null;
            return new ResultView(drillId, title, drill.getScheduledFor(), retry, minutes, words, state, null, null,
                    List.of(), List.of(), List.of(), failure);
        }
        Grade graded = grade.orElseThrow();
        return new ResultView(drillId, title, drill.getScheduledFor(), retry, minutes, words, state,
                graded.getOverall().setScale(0, RoundingMode.HALF_UP).intValue(), graded.getSummary(),
                graded.getImprovements(), dimensionViews(graded), checklist(drill, graded), null);
    }

    private GradingInput load(long drillId) {
        Drill drill = drills.findById(drillId).orElse(null);
        if (drill == null || drill.getStatus() != DrillStatus.GRADING) {
            return null;
        }
        Answer answer = mainAnswer(drillId).orElse(null);
        if (answer == null || grades.existsByAnswerId(answer.getId())) {
            return null;
        }
        List<ChecklistPoint> checklist = drill.getProblem().activeKeyPoints().stream()
                .map(point -> new ChecklistPoint(point.getId(), point.getCode(), point.getDimension().code(),
                        point.getText()))
                .toList();
        Map<String, String> sections = new LinkedHashMap<>();
        for (SectionGuide guide : SectionGuide.values()) {
            sections.put(guide.key(), answer.section(guide.key()));
        }
        return new GradingInput(drillId, answer.getId(), drill.getProblem().getTitle(), drill.getProblem().getPrompt(),
                checklist, sections);
    }

    Grade evaluate(GradingInput input) {
        Usage usage = Usage.NONE;
        String feedback = null;
        for (int round = 1; ; round++) {
            LlmResult result;
            try {
                result = llm.complete(prompt.build(input, feedback));
            } catch (LlmException e) {
                Usage failed = usage.plus(new Usage(null, 0, 0, 0, e.attempts()));
                return Grade.failed(input.answerId(), e.reason().name(), e.getMessage(), failed);
            }
            usage = usage.plus(new Usage(result.model(), result.promptTokens(), result.completionTokens(),
                    result.latencyMs(), result.attempts()));
            boolean lastRound = round == 2;
            try {
                GradeDraft draft = validator.validate(result.json(), input);
                List<ScoreDraft> unverified = draft.unverified();
                if (unverified.size() <= MAX_UNVERIFIED_QUOTES || lastRound) {
                    return toGrade(input, draft, usage);
                }
                feedback = unverified.size() + " evidence quotes are not in the answer: " + unverified.stream()
                        .map(score -> "\"" + score.evidence() + "\"").collect(Collectors.joining(", "))
                        + ". Copy each evidence passage exactly from the answer.";
            } catch (InvalidGradeException e) {
                if (lastRound) {
                    return Grade.failed(input.answerId(), INVALID_RESPONSE, e.getMessage(), usage);
                }
                feedback = e.getMessage();
            }
            log.warn("Drill {} grade round {} rejected: {}", input.drillId(), round, feedback);
        }
    }

    private static Grade toGrade(GradingInput input, GradeDraft draft, Usage usage) {
        Map<Dimension, Integer> scores = new EnumMap<>(Dimension.class);
        draft.scores().forEach(score -> scores.put(score.dimension(), score.score()));
        List<DimensionScore> dimensionScores = draft.scores().stream()
                .map(score -> new DimensionScore(score.dimension(), score.score(), score.evidence(), score.verified(),
                        score.missing()))
                .toList();
        List<KeyPointHit> hits = input.checklist().stream()
                .map(point -> new KeyPointHit(point.id(), draft.keyPoints().getOrDefault(point.code(), false)))
                .toList();
        return Grade.ok(input.answerId(), Rubric.overall(scores), draft.summary(), dimensionScores, hits,
                draft.strengths(), draft.improvements(), draft.followUps(), usage);
    }

    private void save(long drillId, Grade grade) {
        Drill drill = drills.findByIdForUpdate(drillId).orElse(null);
        if (drill == null || drill.getStatus() != DrillStatus.GRADING || grades.existsByAnswerId(grade.getAnswerId())) {
            return;
        }
        grades.save(grade);
        if (grade.getStatus() == GradeStatus.OK) {
            drill.markGraded();
        }
    }

    private Optional<Answer> mainAnswer(long drillId) {
        return answers.findByDrillIdAndRound(drillId, (short) Answer.MAIN_ROUND);
    }

    private static State state(Drill drill, Optional<Grade> grade) {
        if (drill.isEditable()) {
            return State.NOT_SUBMITTED;
        }
        if (grade.isEmpty()) {
            return State.GRADING;
        }
        return grade.get().getStatus() == GradeStatus.OK ? State.GRADED : State.FAILED;
    }

    private static List<DimensionView> dimensionViews(Grade grade) {
        Map<Dimension, DimensionScore> byDimension = new EnumMap<>(Dimension.class);
        grade.getDimensions().forEach(score -> byDimension.put(score.getDimension(), score));
        return Arrays.stream(SectionGuide.values())
                .map(guide -> {
                    DimensionScore score = byDimension.get(Dimension.fromCode(guide.key()));
                    return new DimensionView(guide.title(), score.getScore(), score.getEvidence(),
                            score.isEvidenceVerified(), score.getMissing());
                })
                .toList();
    }

    private static List<ChecklistItem> checklist(Drill drill, Grade grade) {
        Map<Long, Boolean> covered = grade.getKeyPoints().stream()
                .collect(Collectors.toMap(KeyPointHit::getKeyPointId, KeyPointHit::isCovered));
        return drill.getProblem().activeKeyPoints().stream()
                .filter(point -> covered.containsKey(point.getId()))
                .sorted(Comparator.comparing((KeyPoint point) -> !covered.get(point.getId())))
                .map(point -> new ChecklistItem(point.getText(), covered.get(point.getId())))
                .toList();
    }

    static String failureMessage(String reason) {
        return switch (reason == null ? "" : reason) {
            case "NOT_CONFIGURED" -> "Grading is not set up yet: the server has no Groq API key.";
            case "RATE_LIMITED" -> "The grading service hit its rate limit. Your answer is saved. Try again in a minute.";
            case "REJECTED" -> "The grading service rejected the request. Check the API key and model, then try again.";
            case INVALID_RESPONSE -> "The grader's reply could not be used. Your answer is saved. Try again.";
            default -> "The grading service did not respond. Your answer is saved. Try again in a minute.";
        };
    }
}
