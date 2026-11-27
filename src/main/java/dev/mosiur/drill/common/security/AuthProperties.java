package dev.mosiur.drill.common.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("drill.auth")
public record AuthProperties(
        @NotBlank(message = "set DRILL_USER") String username,
        @NotBlank(message = "set DRILL_PASSWORD_HASH")
        @Pattern(regexp = "\\$2[aby]\\$\\d{2}\\$.{53}", message = "DRILL_PASSWORD_HASH must be a BCrypt hash")
        String passwordHash) {
}
