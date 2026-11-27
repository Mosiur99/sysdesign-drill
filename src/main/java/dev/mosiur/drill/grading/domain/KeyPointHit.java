package dev.mosiur.drill.grading.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class KeyPointHit {

    @Column(name = "key_point_id", nullable = false)
    private long keyPointId;

    @Column(nullable = false)
    private boolean covered;

    protected KeyPointHit() {
    }

    public KeyPointHit(long keyPointId, boolean covered) {
        this.keyPointId = keyPointId;
        this.covered = covered;
    }

    public long getKeyPointId() {
        return keyPointId;
    }

    public boolean isCovered() {
        return covered;
    }
}
