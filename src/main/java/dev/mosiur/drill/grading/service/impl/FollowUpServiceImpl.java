package dev.mosiur.drill.grading.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.mosiur.drill.answer.domain.Answer;
import dev.mosiur.drill.answer.model.FollowUpSubmitted;
import dev.mosiur.drill.answer.repository.AnswerRepository;
import dev.mosiur.drill.answer.service.AnswerTooLongException;
import dev.mosiur.drill.answer.service.DrillNotFoundException;
import dev.mosiur.drill.drill.domain.Drill;
import dev.mosiur.drill.drill.domain.DrillStatus;
import dev.mosiur.drill.drill.repository.DrillRepository;
import dev.mosiur.drill.grading.domain.DimensionScore;
import dev.mosiur.drill.grading.domain.FollowUp;
import dev.mosiur.drill.grading.domain.FollowUpResult;
import dev.mosiur.drill.grading.domain.Grade;
import dev.mosiur.drill.grading.domain.Grade.Usage;
import dev.mosiur.drill.grading.domain.GradeStatus;
import dev.mosiur.drill.grading.model.FollowUpInput;
import dev.mosiur.drill.grading.model.FollowUpInput.Question;
import dev.mosiur.drill.grading.model.FollowUpScore;
import dev.mosiur.drill.grading.repository.GradeRepository;
import dev.mosiur.drill.grading.service.FollowUpNotAllowedException;
import dev.mosiur.drill.grading.service.FollowUpService;
import dev.mosiur.drill.grading.service.InvalidGradeException;
import dev.mosiur.drill.llm.model.LlmResult;
import dev.mosiur.drill.llm.service.LlmClient;
import dev.mosiur.drill.llm.service.LlmException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class FollowUpServiceImpl implements FollowUpService {

    static final int MAX_ANSWER_LENGTH = 10_000;
    private static final Logger log = LoggerFactory.getLogger(FollowUpServiceImpl.class);

    private final DrillRepository drills;
    private final AnswerRepository answers;
    private final GradeRepository grades;
    private final LlmClient llm;
    private final FollowUpPrompt prompt;
    private final FollowUpValidator validator;
    private final TransactionTemplate transaction;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public FollowUpServiceImpl(DrillRepository drills, AnswerRepository answers, GradeRepository grades,
                               LlmClient llm, FollowUpPrompt prompt, FollowUpValidator validator,
                               TransactionTemplate transaction, ApplicationEventPublisher events, Clock clock) {
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
    @Transactional
    public void submit(long drillId, Map<String, String> fields) {
        Drill drill = locked(drillId);
        Grade main = mainGrade(drillId).orElse(null);
        if (drill.getStatus() != DrillStatus.GRADED || main == null || main.getFollowUps().isEmpty()) {
            throw new FollowUpNotAllowedException(drillId, "answer follow-ups");
        }
        Map<String, String> sections = new LinkedHashMap<>();
        for (int number = 1; number <= main.getFollowUps().size(); number++) {
            String text = fields.getOrDefault(key(number), "").replace("\r\n", "\n");
            if (text.length() > MAX_ANSWER_LENGTH) {
                throw new AnswerTooLongException(key(number), MAX_ANSWER_LENGTH);
            }
            sections.put(key(number), text);
        }
        Instant now = clock.instant();
        Answer answer = new Answer(drillId, Answer.FOLLOW_UP_ROUND);
        answer.write(sections, now);
        answer.close(now);
        answers.save(answer);
        drill.startFollowUp();
        events.publishEvent(new FollowUpSubmitted(drillId));
    }

    @Override
    @Transactional
    public void skip(long drillId) {
        Drill drill = locked(drillId);
        boolean followUpFailed = drill.getStatus() == DrillStatus.FOLLOW_UP && followUpGrade(drillId)
                .map(grade -> grade.getStatus() == GradeStatus.FAILED).orElse(false);
        Grade main = mainGrade(drillId).orElse(null);
        if (main == null || main.getStatus() != GradeStatus.OK
                || (drill.getStatus() != DrillStatus.GRADED && !followUpFailed)) {
            throw new FollowUpNotAllowedException(drillId, "finish");
        }
        drill.finish(main.getOverall());
    }

    @Override
    public void grade(long drillId) {
        FollowUpInput input = transaction.execute(status -> load(drillId));
        if (input == null) {
            return;
        }
        Grade grade = evaluate(input);
        transaction.executeWithoutResult(status -> save(drillId, grade));
        log.info("Drill {} follow-up graded: {} bonus={} attempts={}", drillId, grade.getStatus(), grade.getBonus(),
                grade.getAttempts());
    }

    @Override
    @Transactional
    public void regrade(long drillId) {
        Drill drill = locked(drillId);
        Grade failed = followUpGrade(drillId).filter(grade -> grade.getStatus() == GradeStatus.FAILED).orElse(null);
        if (drill.getStatus() != DrillStatus.FOLLOW_UP || failed == null) {
            throw new FollowUpNotAllowedException(drillId, "grade follow-ups again");
        }
        grades.delete(failed);
        grades.flush();
        events.publishEvent(new FollowUpSubmitted(drillId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> stuckDrills() {
        return drills.findUngradedFollowUpsBefore(clock.instant().minus(GradingServiceImpl.STUCK_AFTER));
    }

    Grade evaluate(FollowUpInput input) {
        Usage usage = Usage.NONE;
        String feedback = null;
        for (int round = 1; ; round++) {
            LlmResult result;
            try {
                result = llm.complete(prompt.build(input, feedback));
            } catch (LlmException e) {
                return Grade.failed(input.answerId(), e.reason().name(), e.getMessage(),
                        usage.plus(new Usage(null, 0, 0, 0, e.attempts())));
            }
            usage = usage.plus(new Usage(result.model(), result.promptTokens(), result.completionTokens(),
                    result.latencyMs(), result.attempts()));
            try {
                List<FollowUpResult> results = validator.validate(result.json(), input);
                List<FollowUpScore.Answered> answered = results.stream()
                        .map(r -> new FollowUpScore.Answered(r.originalScore(), r.score()))
                        .toList();
                return Grade.followUp(input.answerId(), FollowUpScore.bonus(answered), results, usage);
            } catch (InvalidGradeException e) {
                if (round == 2) {
                    return Grade.failed(input.answerId(), "INVALID_RESPONSE", e.getMessage(), usage);
                }
                feedback = e.getMessage();
            }
        }
    }

    private FollowUpInput load(long drillId) {
        Drill drill = drills.findById(drillId).orElse(null);
        if (drill == null || drill.getStatus() != DrillStatus.FOLLOW_UP) {
            return null;
        }
        Answer followUpAnswer = answer(drillId, Answer.FOLLOW_UP_ROUND).orElse(null);
        Answer mainAnswer = answer(drillId, Answer.MAIN_ROUND).orElse(null);
        Grade main = mainGrade(drillId).orElse(null);
        if (followUpAnswer == null || mainAnswer == null || main == null
                || grades.existsByAnswerId(followUpAnswer.getId())) {
            return null;
        }
        List<Question> questions = new ArrayList<>();
        List<FollowUp> followUps = main.getFollowUps();
        for (int i = 0; i < followUps.size(); i++) {
            FollowUp followUp = followUps.get(i);
            int original = main.getDimensions().stream()
                    .filter(score -> score.getDimension().code().equals(followUp.dimension()))
                    .mapToInt(DimensionScore::getScore).findFirst().orElse(0);
            String section = followUp.dimension() == null ? "" : mainAnswer.section(followUp.dimension());
            questions.add(new Question(i + 1, followUp.dimension(), followUp.question(), original, section,
                    followUpAnswer.section(key(i + 1))));
        }
        return new FollowUpInput(drillId, followUpAnswer.getId(), drill.getProblem().getTitle(),
                drill.getProblem().getPrompt(), questions);
    }

    private void save(long drillId, Grade grade) {
        Drill drill = drills.findByIdForUpdate(drillId).orElse(null);
        if (drill == null || drill.getStatus() != DrillStatus.FOLLOW_UP || grades.existsByAnswerId(grade.getAnswerId())) {
            return;
        }
        grades.save(grade);
        if (grade.getStatus() == GradeStatus.OK) {
            Grade main = mainGrade(drillId).orElseThrow();
            drill.finish(FollowUpScore.finalScore(main.getOverall(), grade.getBonus()));
        }
    }

    private Drill locked(long drillId) {
        return drills.findByIdForUpdate(drillId).orElseThrow(() -> new DrillNotFoundException(drillId));
    }

    private Optional<Answer> answer(long drillId, int round) {
        return answers.findByDrillIdAndRound(drillId, (short) round);
    }

    private Optional<Grade> mainGrade(long drillId) {
        return answer(drillId, Answer.MAIN_ROUND).flatMap(answer -> grades.findByAnswerId(answer.getId()));
    }

    private Optional<Grade> followUpGrade(long drillId) {
        return answer(drillId, Answer.FOLLOW_UP_ROUND).flatMap(answer -> grades.findByAnswerId(answer.getId()));
    }

    static String key(int number) {
        return "follow_up_" + number;
    }
}
