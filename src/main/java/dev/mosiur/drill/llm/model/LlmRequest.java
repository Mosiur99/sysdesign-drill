package dev.mosiur.drill.llm.model;

import java.util.List;

import tools.jackson.databind.JsonNode;

public record LlmRequest(String purpose, List<LlmMessage> messages, String schemaName, JsonNode schema) {
}
