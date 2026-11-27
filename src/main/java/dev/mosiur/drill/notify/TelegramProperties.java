package dev.mosiur.drill.notify;

import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("drill.telegram")
public record TelegramProperties(@NotBlank String baseUrl, String botToken, String chatId, @NotBlank String appUrl,
                                 String tunnelMetricsUrl, @NotNull Duration timeout) {

    public boolean isConfigured() {
        return botToken != null && !botToken.isBlank() && chatId != null && !chatId.isBlank();
    }
}
