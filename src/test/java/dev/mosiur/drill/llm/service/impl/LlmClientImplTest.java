package dev.mosiur.drill.llm.service.impl;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import dev.mosiur.drill.llm.LlmProperties;
import dev.mosiur.drill.llm.model.LlmMessage;
import dev.mosiur.drill.llm.model.LlmRequest;
import dev.mosiur.drill.llm.model.LlmResult;
import dev.mosiur.drill.llm.service.LlmException;
import dev.mosiur.drill.llm.service.LlmException.Reason;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class LlmClientImplTest {

    private static final String KEY = "gsk_test_secret";
    private static final String PATH = "/openai/v1/chat/completions";
    private static final String SCHEMA = """
            {"type": "object", "properties": {"sum": {"type": "integer"}},
             "required": ["sum"], "additionalProperties": false}
            """;

    private final JsonMapper json = JsonMapper.builder().build();
    private final List<Duration> sleeps = new ArrayList<>();
    private WireMockServer server;

    @BeforeEach
    void startServer() {
        server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void sendsAStrictSchemaRequestAndReadsTheResult() {
        server.stubFor(post(PATH).willReturn(ok("{\\\"sum\\\": 4}", "stop")));

        LlmResult result = client(KEY).complete(request());

        assertThat(result.json().path("sum").asInt()).isEqualTo(4);
        assertThat(result.model()).isEqualTo("openai/gpt-oss-120b");
        assertThat(result.promptTokens()).isEqualTo(120);
        assertThat(result.completionTokens()).isEqualTo(30);
        assertThat(result.attempts()).isEqualTo(1);
        server.verify(postRequestedFor(urlEqualTo(PATH))
                .withHeader("Authorization", equalTo("Bearer " + KEY))
                .withRequestBody(equalToJson("""
                        {"model": "openai/gpt-oss-120b", "temperature": 0, "reasoning_effort": "low",
                         "messages": [{"role": "system", "content": "Add numbers."},
                                      {"role": "user", "content": "2 + 2"}],
                         "response_format": {"type": "json_schema",
                           "json_schema": {"name": "sum", "strict": true, "schema": %s}}}
                        """.formatted(SCHEMA))));
        assertThat(sleeps).isEmpty();
    }

    @Test
    void waitsForRetryAfterOnRateLimitThenSucceeds() {
        stubSequence(aResponse().withStatus(429).withHeader("retry-after", "2")
                .withBody("{\"error\": {\"message\": \"Rate limit reached\"}}"), ok("{\\\"sum\\\": 4}", "stop"));

        LlmResult result = client(KEY).complete(request());

        assertThat(result.attempts()).isEqualTo(2);
        assertThat(sleeps).containsExactly(Duration.ofSeconds(2));
    }

    @Test
    void givesUpWhenTheRateLimitWaitIsTooLong() {
        server.stubFor(post(PATH).willReturn(aResponse().withStatus(429).withHeader("retry-after", "120")));

        LlmException error = failure(client(KEY));

        assertThat(error.reason()).isEqualTo(Reason.RATE_LIMITED);
        assertThat(error.attempts()).isEqualTo(1);
        assertThat(sleeps).isEmpty();
    }

    @Test
    void retriesServerErrorsWithBackoffUpToTheLimit() {
        server.stubFor(post(PATH).willReturn(aResponse().withStatus(503)));

        LlmException error = failure(client(KEY));

        assertThat(error.reason()).isEqualTo(Reason.SERVER_ERROR);
        assertThat(error.attempts()).isEqualTo(3);
        assertThat(sleeps).containsExactly(Duration.ofSeconds(1), Duration.ofSeconds(2));
        server.verify(3, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void recoversAfterOneServerError() {
        stubSequence(aResponse().withStatus(500), ok("{\\\"sum\\\": 4}", "stop"));

        assertThat(client(KEY).complete(request()).attempts()).isEqualTo(2);
    }

    @Test
    void neverRetriesARejectedRequestAndKeepsTheKeyOutOfErrors() {
        server.stubFor(post(PATH).willReturn(aResponse().withStatus(401)
                .withBody("{\"error\": {\"message\": \"Invalid API Key\"}}")));

        LlmException error = failure(client(KEY));

        assertThat(error.reason()).isEqualTo(Reason.REJECTED);
        assertThat(error.getMessage()).contains("401 Invalid API Key").doesNotContain(KEY);
        server.verify(1, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void retriesWhenTheModelOutputFailsGroqsSchemaCheck() {
        stubSequence(aResponse().withStatus(400).withBody(
                        "{\"error\": {\"message\": \"Generated JSON does not match the expected schema.\", "
                                + "\"code\": \"json_validate_failed\"}}"),
                ok("{\\\"sum\\\": 4}", "stop"));

        LlmResult result = client(KEY).complete(request());

        assertThat(result.attempts()).isEqualTo(2);
    }

    @Test
    void rejectsATruncatedAnswer() {
        server.stubFor(post(PATH).willReturn(ok("{\\\"sum\\\": ", "length")));

        LlmException error = failure(client(KEY));

        assertThat(error.reason()).isEqualTo(Reason.INVALID_RESPONSE);
        assertThat(error.getMessage()).contains("length");
    }

    @Test
    void rejectsContentThatIsNotJson() {
        server.stubFor(post(PATH).willReturn(ok("four", "stop")));

        assertThat(failure(client(KEY)).reason()).isEqualTo(Reason.INVALID_RESPONSE);
    }

    @Test
    void treatsATimeoutAsANetworkErrorAndRetries() {
        server.stubFor(post(PATH).willReturn(ok("{\\\"sum\\\": 4}", "stop").withFixedDelay(1_000)));

        LlmException error = failure(client(KEY, Duration.ofMillis(200)));

        assertThat(error.reason()).isEqualTo(Reason.NETWORK_ERROR);
        assertThat(error.attempts()).isEqualTo(3);
    }

    @Test
    void failsFastWithoutAnApiKey() {
        LlmException error = failure(client(""));

        assertThat(error.reason()).isEqualTo(Reason.NOT_CONFIGURED);
        server.verify(0, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void readsRetryAfterInSeconds() {
        assertThat(LlmClientImpl.retryAfter("3")).isEqualTo(Duration.ofSeconds(3));
        assertThat(LlmClientImpl.retryAfter("1.5")).isEqualTo(Duration.ofMillis(1_500));
        assertThat(LlmClientImpl.retryAfter("soon")).isNull();
        assertThat(LlmClientImpl.retryAfter(null)).isNull();
    }

    private LlmClientImpl client(String key) {
        return client(key, Duration.ofSeconds(5));
    }

    private LlmClientImpl client(String key, Duration readTimeout) {
        LlmProperties properties = new LlmProperties(server.baseUrl() + "/openai/v1", key, "openai/gpt-oss-120b", "low",
                Duration.ofSeconds(2), readTimeout, Duration.ofSeconds(30), 3);
        return new LlmClientImpl(properties, RestClient.builder(), json, sleeps::add);
    }

    private LlmRequest request() {
        return new LlmRequest("test", List.of(LlmMessage.system("Add numbers."), LlmMessage.user("2 + 2")), "sum",
                json.readTree(SCHEMA));
    }

    private LlmException failure(LlmClientImpl client) {
        return catchThrowableOfType(LlmException.class, () -> client.complete(request()));
    }

    private void stubSequence(ResponseDefinitionBuilder first, ResponseDefinitionBuilder second) {
        server.stubFor(post(PATH).inScenario("retry").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(first).willSetStateTo("second"));
        server.stubFor(post(PATH).inScenario("retry").whenScenarioStateIs("second").willReturn(second));
    }

    private static ResponseDefinitionBuilder ok(String content, String finishReason) {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("""
                {"id": "chatcmpl-1", "model": "openai/gpt-oss-120b",
                 "choices": [{"index": 0, "finish_reason": "%s",
                              "message": {"role": "assistant", "content": "%s"}}],
                 "usage": {"prompt_tokens": 120, "completion_tokens": 30, "total_tokens": 150}}
                """.formatted(finishReason, content));
    }
}
