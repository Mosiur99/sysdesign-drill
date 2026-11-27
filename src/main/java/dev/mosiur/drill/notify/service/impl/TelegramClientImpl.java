package dev.mosiur.drill.notify.service.impl;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

import dev.mosiur.drill.notify.TelegramProperties;
import dev.mosiur.drill.notify.model.SendResult;
import dev.mosiur.drill.notify.service.TelegramClient;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service
public class TelegramClientImpl implements TelegramClient {

    private final TelegramProperties properties;
    private final RestClient http;
    private final JsonMapper json;

    public TelegramClientImpl(TelegramProperties properties, RestClient.Builder builder, JsonMapper json) {
        this.properties = properties;
        this.json = json;
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.timeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());
        this.http = builder.clone().requestFactory(requestFactory).build();
    }

    @Override
    public SendResult send(String html) {
        if (!properties.isConfigured()) {
            return SendResult.rejected("Telegram is not configured");
        }
        Map<String, Object> body = Map.of("chat_id", properties.chatId(), "text", html, "parse_mode", "HTML",
                "link_preview_options", Map.of("is_disabled", true));
        try {
            return http.post()
                    .uri(URI.create(properties.baseUrl().replaceAll("/+$", "") + "/bot" + properties.botToken()
                            + "/sendMessage"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (status == 200) {
                            return SendResult.sent();
                        }
                        JsonNode error = parse(new String(response.getBody().readAllBytes()));
                        String detail = status + " " + error.path("description").asString("");
                        if (status == 429) {
                            long seconds = error.path("parameters").path("retry_after").asLong(5);
                            return SendResult.retry(detail, Duration.ofSeconds(seconds));
                        }
                        return status >= 500 ? SendResult.retry(detail, null) : SendResult.rejected(detail);
                    });
        } catch (ResourceAccessException e) {
            return SendResult.retry("network error: " + e.getMostSpecificCause().getClass().getSimpleName(), null);
        }
    }

    private JsonNode parse(String body) {
        try {
            return json.readTree(body);
        } catch (JacksonException e) {
            return json.createObjectNode();
        }
    }
}
