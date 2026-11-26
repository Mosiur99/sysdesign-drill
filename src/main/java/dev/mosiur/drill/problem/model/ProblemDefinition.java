package dev.mosiur.drill.problem.model;

import java.util.List;
import java.util.Set;

import dev.mosiur.drill.problem.domain.Topic;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProblemDefinition(
        @NotBlank @Pattern(regexp = "[a-z0-9]+(-[a-z0-9]+)*") String slug,
        @NotBlank @Size(max = 120) String title,
        @Min(1) @Max(3) int difficulty,
        @NotEmpty Set<@NotNull Topic> topics,
        @NotBlank String prompt,
        @NotNull @Size(min = 4, max = 10) List<@NotNull @Valid KeyPointDefinition> keyPoints) {
}
