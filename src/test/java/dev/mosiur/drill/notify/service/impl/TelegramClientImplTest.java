package dev.mosiur.drill.notify.service.impl;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.mosiur.drill.notify.TelegramProperties;
import dev.mosiur.drill.notify.model.SendResult;
import dev.mosiur.drill.notify.model.SendResult.Outcome;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class TelegramClientImplTest {

    private static final String TOKEN = "123:secret-token";
    private static final String PATH = "/bot" + TOKEN + "/sendMessage";

    private WireMockServer server;

    @BeforeEach
    void start() {
        server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop();
    }

    @Test
    void sendsHtmlToTheConfiguredChat() {
        server.stubFor(post(PATH).willReturn(aResponse().withStatus(200).withBody("{\"ok\": true}")));

        SendResult sent = client(TOKEN).send("<b>Hi</b>");
        assertThat(sent.outcome()).as(String.valueOf(sent.detail())).isEqualTo(Outcome.SENT);
        server.verify(postRequestedFor(urlEqualTo(PATH)).withRequestBody(equalToJson("""
                {"chat_id": "42", "text": "<b>Hi</b>", "parse_mode": "HTML",
                 "link_preview_options": {"is_disabled": true}}""")));
    }

    @Test
    void honoursRetryAfterOnRateLimit() {
        server.stubFor(post(PATH).willReturn(aResponse().withStatus(429).withBody(
                "{\"ok\": false, \"description\": \"Too Many Requests: retry after 7\", \"parameters\": {\"retry_after\": 7}}")));

        SendResult result = client(TOKEN).send("x");

        assertThat(result.outcome()).isEqualTo(Outcome.RETRY);
        assertThat(result.retryAfter()).isEqualTo(Duration.ofSeconds(7));
    }

    @Test
    void retriesServerErrorsButNotClientErrors() {
        server.stubFor(post(PATH).willReturn(aResponse().withStatus(502)));
        assertThat(client(TOKEN).send("x").outcome()).isEqualTo(Outcome.RETRY);

        server.resetAll();
        server.stubFor(post(PATH).willReturn(aResponse().withStatus(400).withBody(
                "{\"ok\": false, \"description\": \"Bad Request: chat not found\"}")));
        SendResult rejected = client(TOKEN).send("x");

        assertThat(rejected.outcome()).isEqualTo(Outcome.REJECTED);
        assertThat(rejected.detail()).isEqualTo("400 Bad Request: chat not found").doesNotContain(TOKEN);
    }

    @Test
    void doesNotCallTelegramWhenNotConfigured() {
        assertThat(client("").send("x").outcome()).isEqualTo(Outcome.REJECTED);
        server.verify(0, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void networkErrorsAreRetriedWithoutLeakingTheUrl() {
        server.stubFor(post(PATH).willReturn(aResponse().withStatus(200).withFixedDelay(2_000)));

        SendResult result = new TelegramClientImpl(new TelegramProperties(server.baseUrl(), TOKEN, "42",
                "http://localhost:8080", null, Duration.ofMillis(200)), RestClient.builder(), JsonMapper.builder().build())
                .send("x");

        assertThat(result.outcome()).isEqualTo(Outcome.RETRY);
        assertThat(result.detail()).doesNotContain(TOKEN).startsWith("network error");
    }

    private TelegramClientImpl client(String token) {
        return new TelegramClientImpl(new TelegramProperties(server.baseUrl(), token, "42", "http://localhost:8080", null,
                Duration.ofSeconds(2)), RestClient.builder(), JsonMapper.builder().build());
    }
}
