package dev.mosiur.drill.drill.domain;

import java.time.Instant;
import java.time.LocalDate;

import dev.mosiur.drill.problem.domain.Problem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "drill")
public class Drill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "problem_id")
    private Problem problem;

    @Column(name = "scheduled_for", nullable = false)
    private LocalDate scheduledFor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DrillKind kind;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DrillStatus status;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    @Version
    private int version;

    protected Drill() {
    }

    public Drill(Problem problem, LocalDate scheduledFor, DrillKind kind) {
        this.problem = problem;
        this.scheduledFor = scheduledFor;
        this.kind = kind;
        this.status = DrillStatus.ASSIGNED;
    }

    public void start(Instant now) {
        if (status == DrillStatus.ASSIGNED) {
            status = DrillStatus.IN_PROGRESS;
            startedAt = now;
        }
    }

    public void submit(Instant now) {
        if (!isEditable()) {
            throw new IllegalStateException("drill " + id + " is " + status + " and cannot be submitted");
        }
        start(now);
        status = DrillStatus.GRADING;
        submittedAt = now;
    }

    public boolean isEditable() {
        return status == DrillStatus.ASSIGNED || status == DrillStatus.IN_PROGRESS;
    }

    public Long getId() {
        return id;
    }

    public Problem getProblem() {
        return problem;
    }

    public LocalDate getScheduledFor() {
        return scheduledFor;
    }

    public DrillKind getKind() {
        return kind;
    }

    public DrillStatus getStatus() {
        return status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }
}
