package dev.mosiur.drill.grading.service.impl;

import static dev.mosiur.drill.grading.GradingFixtures.gradeJson;
import static dev.mosiur.drill.grading.GradingFixtures.input;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import dev.mosiur.drill.grading.domain.Grade;
import dev.mosiur.drill.grading.domain.GradeStatus;
import dev.mosiur.drill.llm.model.LlmRequest;
import dev.mosiur.drill.llm.model.LlmResult;
import dev.mosiur.drill.llm.service.LlmClient;
import dev.mosiur.drill.llm.service.LlmException;
import dev.mosiur.drill.problem.domain.Dimension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class GradingServiceImplTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Mock
    private LlmClient llm;

    private GradingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new GradingServiceImpl(null, null, null, llm, new GradingPrompt(json), new GradeValidator(), null,
                null, Clock.systemUTC(), null);
    }

    @Test
    void groundedGradeIsAcceptedFirstTime() {
        when(llm.complete(any())).thenReturn(result(gradeJson(Map.of(Dimension.API, 4), Map.of())));

        Grade grade = service.evaluate(input());

        assertThat(grade.getStatus()).isEqualTo(GradeStatus.OK);
        assertThat(grade.getOverall()).isEqualByComparingTo("54.69");
        assertThat(grade.getUnverifiedQuotes()).isZero();
        assertThat(grade.getKeyPoints()).hasSize(2);
        assertThat(grade.getAttempts()).isEqualTo(1);
        verify(llm, times(1)).complete(any());
    }

    @Test
    void retriesOnceWithFeedbackWhenTooManyQuotesAreInvented() {
        Map<Dimension, String> invented = Map.of(Dimension.API, "gRPC streaming endpoint",
                Dimension.DEEP_DIVE, "consistent hashing ring", Dimension.TRADEOFFS, "Cassandra over Postgres");
        when(llm.complete(any()))
                .thenReturn(result(gradeJson(Map.of(), invented)))
                .thenReturn(result(gradeJson(Map.of(), Map.of())));

        Grade grade = service.evaluate(input());

        ArgumentCaptor<LlmRequest> requests = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llm, times(2)).complete(requests.capture());
        assertThat(requests.getAllValues().get(1).messages().getLast().content())
                .contains("3 evidence quotes are not in the answer").contains("consistent hashing ring");
        assertThat(grade.getStatus()).isEqualTo(GradeStatus.OK);
        assertThat(grade.getUnverifiedQuotes()).isZero();
        assertThat(grade.getAttempts()).isEqualTo(2);
    }

    @Test
    void keepsTwoInventedQuotesAsFlagsWithoutRetrying() {
        when(llm.complete(any())).thenReturn(result(gradeJson(Map.of(),
                Map.of(Dimension.API, "gRPC streaming endpoint", Dimension.DEEP_DIVE, "consistent hashing ring"))));

        Grade grade = service.evaluate(input());

        assertThat(grade.getUnverifiedQuotes()).isEqualTo(2);
        verify(llm, times(1)).complete(any());
    }

    @Test
    void failsAfterTwoUnusableReplies() {
        when(llm.complete(any())).thenReturn(result("{\"summary\": \"x\", \"dimensions\": []}"));

        Grade grade = service.evaluate(input());

        assertThat(grade.getStatus()).isEqualTo(GradeStatus.FAILED);
        assertThat(grade.getFailureReason()).isEqualTo("INVALID_RESPONSE");
        verify(llm, times(2)).complete(any());
    }

    @Test
    void recordsTheClientFailureReason() {
        when(llm.complete(any())).thenThrow(new LlmException(LlmException.Reason.RATE_LIMITED, "429", 3));

        Grade grade = service.evaluate(input());

        assertThat(grade.getStatus()).isEqualTo(GradeStatus.FAILED);
        assertThat(grade.getFailureReason()).isEqualTo("RATE_LIMITED");
        assertThat(grade.getAttempts()).isEqualTo(3);
    }

    @Test
    void explainsEachFailureInPlainWords() {
        assertThat(GradingServiceImpl.failureMessage("NOT_CONFIGURED")).contains("no Groq API key");
        assertThat(GradingServiceImpl.failureMessage("RATE_LIMITED")).contains("rate limit");
        assertThat(GradingServiceImpl.failureMessage("NETWORK_ERROR")).contains("did not respond");
        assertThat(List.of("REJECTED", "INVALID_RESPONSE"))
                .allMatch(reason -> GradingServiceImpl.failureMessage(reason).contains("Try again")
                        || GradingServiceImpl.failureMessage(reason).contains("try again"));
    }

    private LlmResult result(String content) {
        return new LlmResult(json.readTree(content), "openai/gpt-oss-120b", 2_400, 900, 1_500, 1);
    }
}
