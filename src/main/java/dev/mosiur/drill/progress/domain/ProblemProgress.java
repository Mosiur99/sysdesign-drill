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

    public Long getProblemId() {
        return problemId;
    }

    public int getAttempts() {
        return attempts;
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
