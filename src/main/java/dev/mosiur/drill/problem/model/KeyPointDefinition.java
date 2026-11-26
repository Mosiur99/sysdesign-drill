package dev.mosiur.drill.problem.model;

import dev.mosiur.drill.problem.domain.Dimension;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record KeyPointDefinition(
        @NotBlank @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*") String id,
        @NotNull Dimension dimension,
        @NotBlank String text) {
}
