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
}
