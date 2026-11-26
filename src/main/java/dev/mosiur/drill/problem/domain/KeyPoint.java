package dev.mosiur.drill.problem.domain;

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

@Entity
@Table(name = "key_point")
public class KeyPoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "problem_id")
    private Problem problem;

    @Column(nullable = false, updatable = false)
    private String code;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Dimension dimension;

    @Column(nullable = false)
    private String text;

    @Column(nullable = false)
    private boolean active = true;

    protected KeyPoint() {
    }

    KeyPoint(Problem problem, String code, Dimension dimension, String text) {
        this.problem = problem;
        this.code = code;
        this.dimension = dimension;
        this.text = text;
    }

    void update(Dimension dimension, String text) {
        this.dimension = dimension;
        this.text = text;
        this.active = true;
    }

    void deactivate() {
        active = false;
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public Dimension getDimension() {
        return dimension;
    }

    public String getText() {
        return text;
    }

    public boolean isActive() {
        return active;
    }
}
