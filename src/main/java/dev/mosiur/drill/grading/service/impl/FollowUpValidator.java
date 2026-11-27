package dev.mosiur.drill.grading.service.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.mosiur.drill.grading.domain.FollowUpResult;
import dev.mosiur.drill.grading.model.FollowUpInput;
import dev.mosiur.drill.grading.model.FollowUpInput.Question;
import dev.mosiur.drill.grading.model.Rubric;
import dev.mosiur.drill.grading.service.InvalidGradeException;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
public class FollowUpValidator {

    public List<FollowUpResult> validate(JsonNode json, FollowUpInput input) {
        Map<Integer, JsonNode> byNumber = new HashMap<>();
        for (JsonNode item : json.path("answers")) {
            byNumber.putIfAbsent(item.path("question").asInt(-1), item);
        }
        List<FollowUpResult> results = new ArrayList<>();
        for (Question question : input.questions()) {
            JsonNode item = byNumber.get(question.number());
            if (item == null) {
                throw new InvalidGradeException("no grade for follow-up question " + question.number() + ".");
            }
            int score = Math.clamp(item.path("score").asInt(0), 0, Rubric.MAX_SCORE);
            String evidence = item.path("evidence").asString("").strip();
            if (question.answer().isBlank()) {
                score = 0;
                evidence = "";
            }
            boolean verified = evidence.isEmpty()
                    ? score == 0
                    : GradeValidator.isQuoted(evidence, GradeValidator.normalise(question.answer()));
            results.add(new FollowUpResult(question.question(), question.dimension(), question.originalScore(), score,
                    evidence, verified, item.path("feedback").asString("").strip()));
        }
        return results;
    }
}
