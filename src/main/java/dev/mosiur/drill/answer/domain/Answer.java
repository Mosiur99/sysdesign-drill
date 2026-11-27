package dev.mosiur.drill.answer.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "answer")
public class Answer {

    public static final int MAIN_ROUND = 1;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "drill_id", nullable = false, updatable = false)
    private long drillId;

    @Column(nullable = false, updatable = false)
    private short round;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, String> sections = new LinkedHashMap<>();

    @Column(name = "word_count", nullable = false)
    private int wordCount;

    @Column(name = "seconds_spent")
    private Integer secondsSpent;

    @Column(nullable = false)
    private boolean draft = true;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Answer() {
    }

    public Answer(long drillId, int round) {
        this.drillId = drillId;
        this.round = (short) round;
    }

    public void write(Map<String, String> sections, Instant now) {
        this.sections = new LinkedHashMap<>(sections);
        this.wordCount = sections.values().stream().mapToInt(Answer::countWords).sum();
        this.updatedAt = now;
    }

    public void finish(Instant startedAt, Instant now) {
        draft = false;
        secondsSpent = (int) Duration.between(startedAt, now).toSeconds();
        updatedAt = now;
    }

    public static int countWords(String text) {
        String trimmed = text.strip();
        return trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
    }

    public String section(String code) {
        return sections.getOrDefault(code, "");
    }

    public long getDrillId() {
        return drillId;
    }

    public int getWordCount() {
        return wordCount;
    }

    public Integer getSecondsSpent() {
        return secondsSpent;
    }

    public boolean isDraft() {
        return draft;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
