package dev.mosiur.drill.llm.service.impl;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import dev.mosiur.drill.llm.LlmProperties;
import dev.mosiur.drill.llm.model.LlmRequest;
import dev.mosiur.drill.llm.model.LlmResult;
import dev.mosiur.drill.llm.service.LlmClient;
import dev.mosiur.drill.llm.service.LlmException;
import dev.mosiur.drill.llm.service.LlmException.Reason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class LlmClientImpl implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(LlmClientImpl.class);
    private static final Duration DEFAULT_RETRY_AFTER = Duration.ofSeconds(2);
    private static final Duration SERVER_ERROR_BACKOFF = Duration.ofSeconds(1);

    private final LlmProperties properties;
    private final RestClient http;
    private final JsonMapper json;
    private final Sleeper sleeper;

    @Autowired
    public LlmClientImpl(LlmProperties properties, RestClient.Builder builder, JsonMapper json) {
        this(properties, builder, json, Sleeper.REAL);
    }

    LlmClientImpl(LlmProperties properties, RestClient.Builder builder, JsonMapper json, Sleeper sleeper) {
        this.properties = properties;
        this.json = json;
        this.sleeper = sleeper;
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        this.http = builder.clone()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeaders(headers -> {
                    if (properties.hasApiKey()) {
                        headers.setBearerAuth(properties.apiKey());
                    }
                })
                .build();
    }

    @Override
    public LlmResult complete(LlmRequest request) {
        if (!properties.hasApiKey()) {
            throw new LlmException(Reason.NOT_CONFIGURED, "set GROQ_API_KEY to call the LLM", 0);
        }
        Map<String, Object> body = body(request);
        long started = System.nanoTime();
        for (int attempt = 1; ; attempt++) {
            Outcome outcome = send(body);
            if (outcome.response() != null) {
                long latencyMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
                LlmResult result = parse(outcome.response(), latencyMs, attempt);
                log.info("LLM {} ok: model={} prompt_tokens={} completion_tokens={} latency_ms={} attempts={}",
                        request.purpose(), result.model(), result.promptTokens(), result.completionTokens(),
                        result.latencyMs(), attempt);
                return result;
            }
            Duration wait = waitBeforeRetry(outcome, attempt);
            log.warn("LLM {} attempt {} failed ({}): {}. Retrying in {} ms", request.purpose(), attempt,
                    outcome.reason(), outcome.detail(), wait.toMillis());
            sleeper.sleep(wait);
        }
    }

    private Map<String, Object> body(LlmRequest request) {
        List<Map<String, String>> messages = request.messages().stream()
                .map(message -> Map.of("role", message.role(), "content", message.content()))
                .toList();
        return Map.of(
                "model", properties.model(),
                "temperature", 0,
                "messages", messages,
                "response_format", Map.of(
                        "type", "json_schema",
                        "json_schema", Map.of("name", request.schemaName(), "strict", true,
                                "schema", request.schema())));
    }

    private Outcome send(Map<String, Object> body) {
        try {
            return http.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .exchange((req, res) -> {
                        int status = res.getStatusCode().value();
                        String text = new String(res.getBody().readAllBytes());
                        if (status == 200) {
                            return Outcome.ok(text);
                        }
                        String detail = status + " " + errorMessage(text);
                        if (status == 429) {
                            return Outcome.failed(Reason.RATE_LIMITED, detail,
                                    retryAfter(res.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)));
                        }
                        if (status >= 500) {
                            return Outcome.failed(Reason.SERVER_ERROR, detail, null);
                        }
                        return Outcome.failed(Reason.REJECTED, detail, null);
                    });
        } catch (ResourceAccessException e) {
            return Outcome.failed(Reason.NETWORK_ERROR, rootMessage(e), null);
        }
    }

    private Duration waitBeforeRetry(Outcome outcome, int attempt) {
        boolean retryable = outcome.reason() != Reason.REJECTED;
        if (!retryable || attempt >= properties.maxAttempts()) {
            throw new LlmException(outcome.reason(), outcome.detail(), attempt);
        }
        if (outcome.reason() == Reason.RATE_LIMITED) {
            Duration wait = outcome.retryAfter() == null ? DEFAULT_RETRY_AFTER : outcome.retryAfter();
            if (wait.compareTo(properties.maxRetryAfter()) > 0) {
                throw new LlmException(Reason.RATE_LIMITED,
                        outcome.detail() + " (retry after " + wait.toSeconds() + " s is too long)", attempt);
            }
            return wait;
        }
        return SERVER_ERROR_BACKOFF.multipliedBy(1L << (attempt - 1));
    }

    private LlmResult parse(String body, long latencyMs, int attempts) {
        try {
            JsonNode root = json.readTree(body);
            JsonNode choice = root.path("choices").path(0);
            String finishReason = choice.path("finish_reason").asString("");
            if (!"stop".equals(finishReason)) {
                throw invalid("finish_reason was '" + finishReason + "'", attempts);
            }
            String content = choice.path("message").path("content").asString("");
            JsonNode answer = json.readTree(content);
            if (!answer.isObject()) {
                throw invalid("content is not a JSON object", attempts);
            }
            JsonNode usage = root.path("usage");
            return new LlmResult(answer, root.path("model").asString(properties.model()),
                    usage.path("prompt_tokens").asInt(0), usage.path("completion_tokens").asInt(0),
                    latencyMs, attempts);
        } catch (JacksonException e) {
            throw invalid("response is not valid JSON", attempts);
        }
    }

    private static LlmException invalid(String detail, int attempts) {
        return new LlmException(Reason.INVALID_RESPONSE, detail, attempts);
    }

    private String errorMessage(String body) {
        try {
            String message = json.readTree(body).path("error").path("message").asString("");
            return message.isBlank() ? "" : message;
        } catch (JacksonException e) {
            return "";
        }
    }

    static Duration retryAfter(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        try {
            return Duration.ofMillis(Math.round(Double.parseDouble(header.strip()) * 1000));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String rootMessage(Exception e) {
        Throwable root = e;
        while (root.getCause() != null) {
            root = root.getCause();
        }
        return root instanceof IOException && root.getMessage() == null
                ? root.getClass().getSimpleName()
                : root.getClass().getSimpleName() + ": " + root.getMessage();
    }

    private record Outcome(String response, Reason reason, String detail, Duration retryAfter) {

        static Outcome ok(String response) {
            return new Outcome(response, null, null, null);
        }

        static Outcome failed(Reason reason, String detail, Duration retryAfter) {
            return new Outcome(null, reason, detail, retryAfter);
        }
    }
}
