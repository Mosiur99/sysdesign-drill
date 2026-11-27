package dev.mosiur.drill.llm.model;

import tools.jackson.databind.JsonNode;

public record LlmResult(JsonNode json, String model, int promptTokens, int completionTokens, long latencyMs,
                        int attempts) {
}
