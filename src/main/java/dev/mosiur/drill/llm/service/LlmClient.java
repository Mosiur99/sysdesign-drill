package dev.mosiur.drill.llm.service;

import dev.mosiur.drill.llm.model.LlmRequest;
import dev.mosiur.drill.llm.model.LlmResult;

public interface LlmClient {

    LlmResult complete(LlmRequest request);
}
