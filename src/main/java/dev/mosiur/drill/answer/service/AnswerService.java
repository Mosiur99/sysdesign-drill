package dev.mosiur.drill.answer.service;

import java.util.Map;

import dev.mosiur.drill.answer.model.EditorView;

public interface AnswerService {

    EditorView open(long drillId);

    void saveDraft(long drillId, Map<String, String> fields);

    void submit(long drillId, Map<String, String> fields);
}
