package dev.mosiur.drill.grading.service.impl;

import static dev.mosiur.drill.grading.GradingFixtures.gradeJson;
import static dev.mosiur.drill.grading.GradingFixtures.input;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.Map;

import dev.mosiur.drill.grading.model.GradeDraft;
import dev.mosiur.drill.grading.model.GradeDraft.ScoreDraft;
import dev.mosiur.drill.grading.model.GradingInput;
import dev.mosiur.drill.grading.service.InvalidGradeException;
import dev.mosiur.drill.problem.domain.Dimension;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

class GradeValidatorTest {

    private final JsonMapper json = JsonMapper.builder().build();
    private final GradeValidator validator = new GradeValidator();

    @Test
    void acceptsAWellFormedGradeWithQuotesFromTheAnswer() {
        GradeDraft draft = validator.validate(json.readTree(gradeJson(Map.of(), Map.of())), input());

        assertThat(draft.scores()).hasSize(8).allMatch(ScoreDraft::verified);
        assertThat(draft.keyPoints()).containsEntry("base62-ids", true).containsEntry("cache-reads", false);
        assertThat(draft.improvements()).hasSize(3);
        assertThat(draft.followUps()).hasSize(2);
        assertThat(draft.summary()).isEqualTo("Clear API, thin on failure handling.");
    }

    @Test
    void matchesQuotesIgnoringCaseWhitespaceAndCurlyQuotes() {
        String answer = GradeValidator.normalise("We use  a counter\n encoded in “base62”.");

        assertThat(GradeValidator.isQuoted("we use a COUNTER encoded in \"base62\"", answer)).isTrue();
        assertThat(GradeValidator.isQuoted("\"We use a counter\"", answer)).isTrue();
        assertThat(GradeValidator.isQuoted("We use ... in “base62”", answer)).isTrue();
        assertThat(GradeValidator.isQuoted("We use random UUIDs", answer)).isFalse();
        assertThat(GradeValidator.isQuoted("...", answer)).isFalse();
    }

    @Test
    void ignoresSectionHeadingsCopiedIntoAQuote() {
        String answer = GradeValidator.normalise("Users shorten long links.\nReads dominate.");

        assertThat(GradeValidator.isQuoted("## requirements\nUsers shorten long links.", answer)).isTrue();
        assertThat(GradeValidator.isQuoted("## requirements", answer)).isFalse();
    }

    @Test
    void flagsInventedQuotes() {
        GradeDraft draft = validator.validate(json.readTree(gradeJson(Map.of(),
                Map.of(Dimension.DEEP_DIVE, "we shard by user id with consistent hashing"))), input());

        assertThat(draft.unverified()).extracting(ScoreDraft::dimension).containsExactly(Dimension.DEEP_DIVE);
    }

    @Test
    void clampsScoresIntoRange() {
        ObjectNode grade = (ObjectNode) json.readTree(gradeJson(Map.of(Dimension.API, 7, Dimension.ESTIMATION, -2),
                Map.of()));

        GradeDraft draft = validator.validate(grade, input());

        assertThat(score(draft, Dimension.API).score()).isEqualTo(4);
        assertThat(score(draft, Dimension.ESTIMATION).score()).isZero();
    }

    @Test
    void emptySectionWithoutEvidenceScoresZero() {
        Map<String, String> sections = new LinkedHashMap<>(input().sections());
        sections.put("tradeoffs", "  ");
        GradingInput input = new GradingInput(5, 9, "t", "p", input().checklist(), sections);

        GradeDraft draft = validator.validate(json.readTree(gradeJson(Map.of(Dimension.TRADEOFFS, 3),
                Map.of(Dimension.TRADEOFFS, ""))), input);

        assertThat(score(draft, Dimension.TRADEOFFS).score()).isZero();
        assertThat(score(draft, Dimension.TRADEOFFS).verified()).isTrue();
    }

    @Test
    void aPositiveScoreWithoutEvidenceIsUnverified() {
        GradeDraft draft = validator.validate(json.readTree(gradeJson(Map.of(Dimension.API, 3),
                Map.of(Dimension.API, ""))), input());

        assertThat(score(draft, Dimension.API).verified()).isFalse();
    }

    @Test
    void rejectsAGradeWithAMissingDimension() {
        ObjectNode grade = (ObjectNode) json.readTree(gradeJson(Map.of(), Map.of()));
        ((ArrayNode) grade.get("dimensions")).remove(7);

        assertThatThrownBy(() -> validator.validate(grade, input()))
                .isInstanceOf(InvalidGradeException.class).hasMessageContaining("tradeoffs");
    }

    @Test
    void rejectsAGradeWithoutImprovements() {
        ObjectNode grade = (ObjectNode) json.readTree(gradeJson(Map.of(), Map.of()));
        grade.putArray("improvements");

        assertThatThrownBy(() -> validator.validate(grade, input())).isInstanceOf(InvalidGradeException.class);
    }

    @Test
    void ignoresUnknownKeyPointsAndCapsLists() {
        ObjectNode grade = (ObjectNode) json.readTree(gradeJson(Map.of(), Map.of()));
        grade.putArray("key_points").addObject().put("code", "invented").put("covered", true);
        grade.putArray("improvements").add("a").add("b").add("c").add("d");

        GradeDraft draft = validator.validate(grade, input());

        assertThat(draft.keyPoints()).containsOnlyKeys("base62-ids", "cache-reads").doesNotContainValue(true);
        assertThat(draft.improvements()).containsExactly("a", "b", "c");
    }

    private static ScoreDraft score(GradeDraft draft, Dimension dimension) {
        return draft.scores().stream().filter(score -> score.dimension() == dimension).findFirst().orElseThrow();
    }
}
