package dev.mosiur.drill.grading.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;

import dev.mosiur.drill.grading.domain.Grade;
import dev.mosiur.drill.grading.domain.GradeStatus;
import dev.mosiur.drill.llm.model.LlmRequest;
import dev.mosiur.drill.llm.model.LlmResult;
import dev.mosiur.drill.llm.service.LlmClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class FollowUpServiceImplTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Mock
    private LlmClient llm;

    private FollowUpServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new FollowUpServiceImpl(null, null, null, llm, new FollowUpPrompt(json), new FollowUpValidator(),
                null, null, Clock.systemUTC());
    }

    @Test
    void scoresTheImprovementOnEachTargetedSection() {
        when(llm.complete(any())).thenReturn(result("""
                {"answers": [
                  {"question": 1, "score": 3, "evidence": "Redirects keep working from the cache", "feedback": "Good."},
                  {"question": 2, "score": 2, "evidence": "ranges of IDs", "feedback": "Same as before."}]}
                """));

        Grade grade = service.evaluate(FollowUpValidatorTest.input("Each server takes ranges of IDs."));

        assertThat(grade.getStatus()).isEqualTo(GradeStatus.OK);
        assertThat(grade.getBonus()).isEqualByComparingTo("3.33");
        assertThat(grade.getFollowUpResults()).hasSize(2);
        ArgumentCaptor<LlmRequest> request = ArgumentCaptor.forClass(LlmRequest.class);
        verify(llm).complete(request.capture());
        assertThat(request.getValue().schemaName()).isEqualTo(FollowUpPrompt.SCHEMA_NAME);
        assertThat(request.getValue().messages().get(1).content())
                .contains("Question 1 (dimension: scale_failure, earlier score 1/4)")
                .contains("<follow_up_answer>\nEach server takes ranges of IDs.\n</follow_up_answer>");
    }

    @Test
    void retriesOnceAndThenFails() {
        when(llm.complete(any())).thenReturn(result("{\"answers\": []}"));

        Grade grade = service.evaluate(FollowUpValidatorTest.input("x"));

        assertThat(grade.getStatus()).isEqualTo(GradeStatus.FAILED);
        verify(llm, times(2)).complete(any());
    }

    private LlmResult result(String content) {
        return new LlmResult(json.readTree(content), "openai/gpt-oss-120b", 900, 200, 800, 1);
    }
}
