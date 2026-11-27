package dev.mosiur.drill.notify.service.impl;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Optional;

import dev.mosiur.drill.notify.TelegramProperties;
import dev.mosiur.drill.notify.service.AppUrl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Service
public class AppUrlImpl implements AppUrl {

    private static final Logger log = LoggerFactory.getLogger(AppUrlImpl.class);

    private final TelegramProperties properties;
    private final RestClient http;
    private final JsonMapper json;

    public AppUrlImpl(TelegramProperties properties, RestClient.Builder builder, JsonMapper json) {
        this.properties = properties;
        this.json = json;
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(3))
                .build());
        requestFactory.setReadTimeout(Duration.ofSeconds(3));
        this.http = builder.clone().requestFactory(requestFactory).build();
    }

    @Override
    public String current() {
        return tunnel().orElse(properties.appUrl());
    }

    @Override
    public Optional<String> tunnel() {
        String metrics = properties.tunnelMetricsUrl();
        if (metrics == null || metrics.isBlank()) {
            return Optional.empty();
        }
        try {
            String body = http.get().uri(URI.create(metrics.replaceAll("/+$", "") + "/quicktunnel"))
                    .retrieve().body(String.class);
            String hostname = body == null ? "" : json.readTree(body).path("hostname").asString("");
            return hostname.isBlank() ? Optional.empty() : Optional.of("https://" + hostname);
        } catch (RestClientException | JacksonException e) {
            log.warn("Could not read the tunnel address, using {}: {}", properties.appUrl(), e.getMessage());
            return Optional.empty();
        }
    }
}
