package dev.mosiur.drill.grading.domain;

public record FollowUpResult(String question, String dimension, int originalScore, int score, String evidence,
                             boolean verified, String feedback) {
}
