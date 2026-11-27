package dev.mosiur.drill.answer.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.mosiur.drill.answer.domain.Answer;
import dev.mosiur.drill.answer.model.EditorSection;
import dev.mosiur.drill.answer.model.AnswerSubmitted;
import dev.mosiur.drill.answer.model.EditorView;
import dev.mosiur.drill.answer.model.SectionGuide;
import dev.mosiur.drill.answer.repository.AnswerRepository;
import dev.mosiur.drill.answer.service.AnswerLockedException;
import dev.mosiur.drill.answer.service.AnswerService;
import dev.mosiur.drill.answer.service.AnswerTooLongException;
import dev.mosiur.drill.answer.service.DrillNotFoundException;
import dev.mosiur.drill.drill.domain.Drill;
import dev.mosiur.drill.drill.repository.DrillRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnswerServiceImpl implements AnswerService {

    static final int MAX_SECTION_LENGTH = 20_000;

    private final DrillRepository drills;
    private final AnswerRepository answers;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    public AnswerServiceImpl(DrillRepository drills, AnswerRepository answers, Clock clock,
                             ApplicationEventPublisher events) {
        this.drills = drills;
        this.answers = answers;
        this.clock = clock;
        this.events = events;
    }

    @Override
    @Transactional
    public EditorView open(long drillId) {
        Drill drill = find(drillId);
        drill.start(clock.instant());
        Answer answer = mainAnswer(drillId);
        List<EditorSection> sections = Arrays.stream(SectionGuide.values())
                .map(guide -> {
                    String text = answer == null ? "" : answer.section(guide.key());
                    return new EditorSection(guide.ordinal() + 1, guide.key(), guide.title(), guide.hint(),
                            guide.rows(), guide.code(), guide.diagram(), text, Answer.countWords(text));
                })
                .toList();
        return new EditorView(drillId, drill.getProblem().getTitle(), drill.getProblem().getPrompt(),
                drill.getStartedAt(), drill.isEditable(), sections);
    }

    @Override
    @Transactional
    public void saveDraft(long drillId, Map<String, String> fields) {
        Drill drill = editable(drillId);
        drill.start(clock.instant());
        write(drillId, sections(fields));
    }

    @Override
    @Transactional
    public void submit(long drillId, Map<String, String> fields) {
        Drill drill = editable(drillId);
        Instant now = clock.instant();
        drill.submit(now);
        Answer answer = write(drillId, sections(fields));
        answer.finish(drill.getStartedAt(), now);
        events.publishEvent(new AnswerSubmitted(drillId));
    }

    private Drill find(long drillId) {
        return drills.findById(drillId).orElseThrow(() -> new DrillNotFoundException(drillId));
    }

    private Drill editable(long drillId) {
        Drill drill = drills.findByIdForUpdate(drillId).orElseThrow(() -> new DrillNotFoundException(drillId));
        if (!drill.isEditable()) {
            throw new AnswerLockedException(drillId);
        }
        return drill;
    }

    private Answer mainAnswer(long drillId) {
        return answers.findByDrillIdAndRound(drillId, (short) Answer.MAIN_ROUND).orElse(null);
    }

    private Answer write(long drillId, Map<String, String> sections) {
        Answer answer = mainAnswer(drillId);
        if (answer == null) {
            answer = new Answer(drillId, Answer.MAIN_ROUND);
        }
        answer.write(sections, clock.instant());
        return answers.save(answer);
    }

    static Map<String, String> sections(Map<String, String> fields) {
        Map<String, String> sections = new LinkedHashMap<>();
        for (SectionGuide guide : SectionGuide.values()) {
            String text = fields.getOrDefault(guide.key(), "").replace("\r\n", "\n");
            if (text.length() > MAX_SECTION_LENGTH) {
                throw new AnswerTooLongException(guide.key(), MAX_SECTION_LENGTH);
            }
            sections.put(guide.key(), text);
        }
        return sections;
    }
}
