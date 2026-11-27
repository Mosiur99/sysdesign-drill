package dev.mosiur.drill.llm;

import java.time.Duration;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("drill.llm")
public record LlmProperties(
        @NotBlank String baseUrl,
        String apiKey,
        @NotBlank String model,
        @NotNull Duration connectTimeout,
        @NotNull Duration readTimeout,
        @NotNull Duration maxRetryAfter,
        @Min(1) @Max(5) int maxAttempts) {

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
