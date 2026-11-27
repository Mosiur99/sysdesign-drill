package dev.mosiur.drill.grading.model;

import java.util.List;

public record FollowUpInput(long drillId, long answerId, String problemTitle, String problemPrompt,
                            List<Question> questions) {

    public record Question(int number, String dimension, String question, int originalScore, String originalSection,
                           String answer) {
    }
}
