package dev.mosiur.drill.grading.model;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

public record ResultView(long drillId, String title, LocalDate day, boolean retry, Integer minutes, int wordCount,
                         State state, Score score, String summary, List<String> improvements,
                         List<DimensionView> dimensions, List<ChecklistItem> checklist, List<FollowUpView> followUps,
                         String failure) {

    public enum State {
        NOT_SUBMITTED,
        GRADING,
        FAILED,
        GRADED,
        FOLLOW_UP_GRADING,
        FOLLOW_UP_FAILED,
        DONE
    }

    public record Score(int main, Integer bonus, int total, boolean followUpsSkipped) {
    }

    public record DimensionView(String title, int score, String evidence, boolean verified, List<String> missing) {

        public List<Boolean> segments() {
            return IntStream.rangeClosed(1, Rubric.MAX_SCORE).mapToObj(segment -> segment <= score).toList();
        }
    }

    public record ChecklistItem(String text, boolean covered) {
    }

    public record FollowUpView(int number, String question, String sectionTitle, String answer, Integer score,
                               String evidence, boolean verified, String feedback) {

        public String field() {
            return "follow_up_" + number;
        }

        public boolean isGraded() {
            return score != null;
        }

        public List<Boolean> segments() {
            int filled = score == null ? 0 : score;
            return IntStream.rangeClosed(1, Rubric.MAX_SCORE).mapToObj(segment -> segment <= filled).toList();
        }
    }

    public long coveredCount() {
        return checklist.stream().filter(ChecklistItem::covered).count();
    }

    public boolean isPending() {
        return state == State.GRADING || state == State.FOLLOW_UP_GRADING;
    }

    public boolean isGrading() {
        return state == State.GRADING;
    }

    public boolean isFailed() {
        return state == State.FAILED;
    }

    public boolean hasGrade() {
        return score != null;
    }

    public boolean canAnswerFollowUps() {
        return state == State.GRADED && !followUps.isEmpty();
    }

    public boolean isFollowUpGrading() {
        return state == State.FOLLOW_UP_GRADING;
    }

    public boolean isFollowUpFailed() {
        return state == State.FOLLOW_UP_FAILED;
    }

    public boolean isDone() {
        return state == State.DONE;
    }
}
