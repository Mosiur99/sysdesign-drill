package dev.mosiur.drill.drill.model;

import dev.mosiur.drill.drill.domain.DrillKind;

public record Pick(long problemId, DrillKind kind) {
}
