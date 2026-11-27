package dev.mosiur.drill.llm;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import dev.mosiur.drill.PostgresTestConfiguration;
import dev.mosiur.drill.llm.model.LlmMessage;
import dev.mosiur.drill.llm.model.LlmRequest;
import dev.mosiur.drill.llm.model.LlmResult;
import dev.mosiur.drill.llm.service.LlmClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
@EnabledIfEnvironmentVariable(named = "GROQ_API_KEY", matches = ".+")
class LlmLiveSmokeIT {

    private static final String SCHEMA = """
            {"type": "object",
             "properties": {"sum": {"type": "integer"}, "explanation": {"type": "string"}},
             "required": ["sum", "explanation"], "additionalProperties": false}
            """;

    @Autowired
    private LlmClient llm;

    @Autowired
    private JsonMapper json;

    @Test
    void realModelReturnsJsonThatMatchesTheSchema() {
        LlmResult result = llm.complete(new LlmRequest("smoke", List.of(
                LlmMessage.system("You add numbers. Reply only with the JSON the schema asks for."),
                LlmMessage.user("What is 17 + 25?")), "sum", json.readTree(SCHEMA)));

        assertThat(result.json().path("sum").asInt()).isEqualTo(42);
        assertThat(result.json().path("explanation").asString()).isNotBlank();
        assertThat(result.promptTokens()).isPositive();
        assertThat(result.completionTokens()).isPositive();
    }
}
