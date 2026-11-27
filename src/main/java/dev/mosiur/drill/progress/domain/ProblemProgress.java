package dev.mosiur.drill.progress.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "problem_progress")
public class ProblemProgress {

    static final BigDecimal STRONG = BigDecimal.valueOf(75);
    static final BigDecimal WEAK = BigDecimal.valueOf(50);
    static final int WEAK_RETRY_DAYS = 3;
    static final int MIDDLE_RETRY_DAYS = 10;
    static final int STRONG_RETRY_DAYS = 30;

    @Id
    @Column(name = "problem_id")
    private Long problemId;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "best_score")
    private BigDecimal bestScore;

    @Column(name = "last_score")
    private BigDecimal lastScore;

    @Column(name = "last_attempted_on")
    private LocalDate lastAttemptedOn;

    @Column(name = "next_due")
    private LocalDate nextDue;

    @Column(nullable = false)
    private boolean retired;

    protected ProblemProgress() {
    }

    public ProblemProgress(long problemId) {
        this.problemId = problemId;
    }

    public void record(BigDecimal score, LocalDate day) {
        boolean strongBefore = bestScore != null && bestScore.compareTo(STRONG) >= 0;
        attempts++;
        lastScore = score;
        lastAttemptedOn = day;
        bestScore = bestScore == null ? score : bestScore.max(score);
        if (score.compareTo(STRONG) >= 0 && strongBefore) {
            retired = true;
            nextDue = null;
            return;
        }
        nextDue = day.plusDays(retryAfterDays(score));
    }

    static int retryAfterDays(BigDecimal score) {
        if (score.compareTo(WEAK) < 0) {
            return WEAK_RETRY_DAYS;
        }
        return score.compareTo(STRONG) < 0 ? MIDDLE_RETRY_DAYS : STRONG_RETRY_DAYS;
    }

    public Long getProblemId() {
        return problemId;
    }

    public int getAttempts() {
        return attempts;
    }

    public BigDecimal getBestScore() {
        return bestScore;
    }

    public BigDecimal getLastScore() {
        return lastScore;
    }

    public LocalDate getLastAttemptedOn() {
        return lastAttemptedOn;
    }

    public LocalDate getNextDue() {
        return nextDue;
    }

    public boolean isRetired() {
        return retired;
    }
}
