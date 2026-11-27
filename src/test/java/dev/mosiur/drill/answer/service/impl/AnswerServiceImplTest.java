package dev.mosiur.drill.answer.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import dev.mosiur.drill.answer.domain.Answer;
import dev.mosiur.drill.answer.model.EditorView;
import dev.mosiur.drill.answer.repository.AnswerRepository;
import dev.mosiur.drill.answer.service.AnswerLockedException;
import dev.mosiur.drill.answer.service.AnswerTooLongException;
import dev.mosiur.drill.answer.service.DrillNotFoundException;
import dev.mosiur.drill.drill.domain.Drill;
import dev.mosiur.drill.drill.domain.DrillKind;
import dev.mosiur.drill.drill.domain.DrillStatus;
import dev.mosiur.drill.drill.repository.DrillRepository;
import dev.mosiur.drill.problem.domain.Problem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AnswerServiceImplTest {

    private static final Instant NOW = Instant.parse("2026-11-27T03:00:00Z");

    @Mock
    private DrillRepository drills;

    @Mock
    private AnswerRepository answers;

    private AnswerServiceImpl service;
    private Drill drill;

    @BeforeEach
    void setUp() {
        service = new AnswerServiceImpl(drills, answers, Clock.fixed(NOW, ZoneOffset.UTC));
        Problem problem = new Problem("url-shortener");
        problem.update("Design a URL shortener", "Shorten links.", 1, java.util.Set.of());
        drill = new Drill(problem, LocalDate.of(2026, 11, 27), DrillKind.NEW);
    }

    @Test
    void keepsOnlyTheEightSectionsAndNormalisesLineEndings() {
        Map<String, String> sections = AnswerServiceImpl.sections(
                Map.of("_csrf", "token", "requirements", "a\r\nb", "unknown", "x"));

        assertThat(sections).hasSize(8).containsEntry("requirements", "a\nb").containsEntry("api", "")
                .doesNotContainKeys("_csrf", "unknown");
    }

    @Test
    void rejectsASectionOverTheLimit() {
        String tooLong = "x".repeat(AnswerServiceImpl.MAX_SECTION_LENGTH + 1);

        assertThatThrownBy(() -> AnswerServiceImpl.sections(Map.of("api", tooLong)))
                .isInstanceOf(AnswerTooLongException.class);
    }

    @Test
    void openingStartsTheTimerAndShowsEmptySections() {
        when(drills.findById(1L)).thenReturn(Optional.of(drill));
        when(answers.findByDrillIdAndRound(1L, (short) 1)).thenReturn(Optional.empty());

        EditorView editor = service.open(1L);

        assertThat(drill.getStatus()).isEqualTo(DrillStatus.IN_PROGRESS);
        assertThat(editor.startedAt()).isEqualTo(NOW);
        assertThat(editor.sections()).hasSize(8).allMatch(section -> section.text().isEmpty());
        assertThat(editor.sections().get(4).diagram()).isTrue();
    }

    @Test
    void savesADraftForAnOpenDrill() {
        when(drills.findByIdForUpdate(1L)).thenReturn(Optional.of(drill));
        when(answers.findByDrillIdAndRound(1L, (short) 1)).thenReturn(Optional.empty());

        service.saveDraft(1L, Map.of("requirements", "shorten long links"));

        verify(answers).save(any(Answer.class));
        assertThat(drill.getStatus()).isEqualTo(DrillStatus.IN_PROGRESS);
    }

    @Test
    void submittingLocksTheDrillAndFinishesTheAnswer() {
        when(drills.findByIdForUpdate(1L)).thenReturn(Optional.of(drill));
        when(answers.findByDrillIdAndRound(1L, (short) 1)).thenReturn(Optional.empty());
        when(answers.save(any(Answer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.submit(1L, Map.of("requirements", "shorten long links"));

        assertThat(drill.getStatus()).isEqualTo(DrillStatus.GRADING);
    }

    @Test
    void refusesChangesAfterSubmitting() {
        drill.submit(NOW);
        when(drills.findByIdForUpdate(1L)).thenReturn(Optional.of(drill));

        assertThatThrownBy(() -> service.saveDraft(1L, Map.of())).isInstanceOf(AnswerLockedException.class);
        assertThatThrownBy(() -> service.submit(1L, Map.of())).isInstanceOf(AnswerLockedException.class);
        verify(answers, never()).save(any());
    }

    @Test
    void reportsAMissingDrill() {
        when(drills.findById(9L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.open(9L)).isInstanceOf(DrillNotFoundException.class);
    }
}
