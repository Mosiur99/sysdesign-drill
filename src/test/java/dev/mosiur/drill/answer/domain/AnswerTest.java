package dev.mosiur.drill.answer.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

class AnswerTest {

    private static final Instant STARTED = Instant.parse("2026-11-27T03:00:00Z");

    @Test
    void countsWordsSeparatedByAnyWhitespace() {
        assertThat(Answer.countWords("")).isZero();
        assertThat(Answer.countWords("  \n\t ")).isZero();
        assertThat(Answer.countWords("POST /links\n  { \"url\": \"x\" }")).isEqualTo(6);
    }

    @Test
    void writingKeepsSectionsAndTotalsTheWords() {
        Answer answer = new Answer(7, Answer.MAIN_ROUND);

        answer.write(Map.of("requirements", "shorten long links", "api", "POST /links"), STARTED);

        assertThat(answer.section("requirements")).isEqualTo("shorten long links");
        assertThat(answer.section("tradeoffs")).isEmpty();
        assertThat(answer.getWordCount()).isEqualTo(5);
        assertThat(answer.isDraft()).isTrue();
    }

    @Test
    void finishingRecordsTheTimeSpent() {
        Answer answer = new Answer(7, Answer.MAIN_ROUND);
        answer.write(Map.of(), STARTED);

        answer.finish(STARTED, STARTED.plusSeconds(2_820));

        assertThat(answer.isDraft()).isFalse();
        assertThat(answer.getSecondsSpent()).isEqualTo(2_820);
    }
}
