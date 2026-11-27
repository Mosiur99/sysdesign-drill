package dev.mosiur.drill.grading.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import dev.mosiur.drill.grading.domain.FollowUpResult;
import dev.mosiur.drill.grading.model.FollowUpInput;
import dev.mosiur.drill.grading.model.FollowUpInput.Question;
import dev.mosiur.drill.grading.service.InvalidGradeException;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class FollowUpValidatorTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private final FollowUpValidator validator = new FollowUpValidator();

    static FollowUpInput input(String secondAnswer) {
        return new FollowUpInput(5, 11, "Design a URL shortener", "Shorten long links.", List.of(
                new Question(1, "scale_failure", "What if the database is down?", 1, "Read replicas.",
                        "Redirects keep working from the cache; writes fail fast and the client retries."),
                new Question(2, "deep_dive", "How do two links never share a code?", 2, "A counter.",
                        secondAnswer)));
    }

    @Test
    void checksQuotesAgainstEachFollowUpAnswer() {
        List<FollowUpResult> results = validator.validate(json.readTree("""
                {"answers": [
                  {"question": 2, "score": 3, "evidence": "takes ranges of IDs", "feedback": "Good."},
                  {"question": 1, "score": 9, "evidence": "Redirects keep working from the cache", "feedback": "Ok."}]}
                """), input("Each server takes ranges of IDs from a central counter."));

        assertThat(results).extracting(FollowUpResult::score).containsExactly(4, 3);
        assertThat(results).allMatch(FollowUpResult::verified);
        assertThat(results.getFirst().originalScore()).isEqualTo(1);
    }

    @Test
    void anEmptyFollowUpScoresZero() {
        List<FollowUpResult> results = validator.validate(json.readTree("""
                {"answers": [
                  {"question": 1, "score": 2, "evidence": "invented text", "feedback": ""},
                  {"question": 2, "score": 3, "evidence": "something", "feedback": ""}]}
                """), input("  "));

        assertThat(results.get(0).verified()).isFalse();
        assertThat(results.get(1).score()).isZero();
        assertThat(results.get(1).verified()).isTrue();
    }

    @Test
    void rejectsAMissingQuestion() {
        assertThatThrownBy(() -> validator.validate(json.readTree("""
                {"answers": [{"question": 1, "score": 2, "evidence": "", "feedback": ""}]}
                """), input("x"))).isInstanceOf(InvalidGradeException.class).hasMessageContaining("question 2");
    }
}
