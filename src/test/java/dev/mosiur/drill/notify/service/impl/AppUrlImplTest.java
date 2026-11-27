package dev.mosiur.drill.notify.service.impl;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.mosiur.drill.notify.TelegramProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class AppUrlImplTest {

    private WireMockServer tunnel;

    @BeforeEach
    void start() {
        tunnel = new WireMockServer(wireMockConfig().dynamicPort());
        tunnel.start();
    }

    @AfterEach
    void stop() {
        tunnel.stop();
    }

    @Test
    void usesTheConfiguredAddressWithoutATunnel() {
        assertThat(appUrl(null).current()).isEqualTo("http://localhost:8080");
        assertThat(appUrl(null).tunnel()).isEmpty();
    }

    @Test
    void asksTheQuickTunnelForItsCurrentAddress() {
        tunnel.stubFor(get("/quicktunnel").willReturn(aResponse().withHeader("Content-Type", "text/plain;charset=utf-8")
                .withBody("{\"hostname\":\"calm-river-1234.trycloudflare.com\"}")));

        assertThat(appUrl(tunnel.baseUrl()).current()).isEqualTo("https://calm-river-1234.trycloudflare.com");
        assertThat(appUrl(tunnel.baseUrl()).tunnel()).contains("https://calm-river-1234.trycloudflare.com");
    }

    @Test
    void fallsBackWhenTheTunnelHasNoAddressYet() {
        tunnel.stubFor(get("/quicktunnel").willReturn(aResponse().withHeader("Content-Type", "application/json")
                .withBody("{\"hostname\": \"\"}")));
        assertThat(appUrl(tunnel.baseUrl()).current()).isEqualTo("http://localhost:8080");

        tunnel.stop();
        assertThat(appUrl("http://localhost:1").current()).isEqualTo("http://localhost:8080");
    }

    private AppUrlImpl appUrl(String metrics) {
        return new AppUrlImpl(new TelegramProperties("https://api.telegram.org", "t", "1", "http://localhost:8080",
                metrics, Duration.ofSeconds(2)), RestClient.builder(), JsonMapper.builder().build());
    }
}
