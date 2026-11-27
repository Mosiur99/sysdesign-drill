package dev.mosiur.drill.grading.domain;

import java.util.ArrayList;
import java.util.List;

import dev.mosiur.drill.problem.domain.Dimension;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Embeddable
public class DimensionScore {

    @Convert(converter = DimensionConverter.class)
    @Column(nullable = false)
    private Dimension dimension;

    @Column(nullable = false)
    private short score;

    @Column(nullable = false)
    private String evidence;

    @Column(name = "evidence_verified", nullable = false)
    private boolean evidenceVerified;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> missing = new ArrayList<>();

    protected DimensionScore() {
    }

    public DimensionScore(Dimension dimension, int score, String evidence, boolean evidenceVerified,
                          List<String> missing) {
        this.dimension = dimension;
        this.score = (short) score;
        this.evidence = evidence;
        this.evidenceVerified = evidenceVerified;
        this.missing = new ArrayList<>(missing);
    }

    public Dimension getDimension() {
        return dimension;
    }

    public int getScore() {
        return score;
    }

    public String getEvidence() {
        return evidence;
    }

    public boolean isEvidenceVerified() {
        return evidenceVerified;
    }

    public List<String> getMissing() {
        return List.copyOf(missing);
    }
}
