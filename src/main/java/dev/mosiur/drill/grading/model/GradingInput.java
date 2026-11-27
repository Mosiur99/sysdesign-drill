package dev.mosiur.drill.grading.model;

import java.util.List;
import java.util.Map;

public record GradingInput(long drillId, long answerId, String problemTitle, String problemPrompt,
                           List<ChecklistPoint> checklist, Map<String, String> sections) {

    public String answerText() {
        return String.join("\n", sections.values());
    }
}
