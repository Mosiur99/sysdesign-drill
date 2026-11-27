package dev.mosiur.drill.grading.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "grade")
public class Grade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "answer_id", nullable = false, updatable = false)
    private long answerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GradeStatus status;

    private BigDecimal overall;

    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> strengths = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<String> improvements = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "follow_ups", nullable = false)
    private List<FollowUp> followUps = new ArrayList<>();

    private BigDecimal bonus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "follow_up_results", nullable = false)
    private List<FollowUpResult> followUpResults = new ArrayList<>();

    @Column(name = "unverified_quotes", nullable = false)
    private int unverifiedQuotes;

    private String model;

    @Column(name = "prompt_tokens", nullable = false)
    private int promptTokens;

    @Column(name = "completion_tokens", nullable = false)
    private int completionTokens;

    @Column(name = "latency_ms", nullable = false)
    private long latencyMs;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "failure_detail")
    private String failureDetail;

    @ElementCollection
    @CollectionTable(name = "dimension_score", joinColumns = @JoinColumn(name = "grade_id"))
    private List<DimensionScore> dimensions = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "key_point_hit", joinColumns = @JoinColumn(name = "grade_id"))
    private List<KeyPointHit> keyPoints = new ArrayList<>();

    protected Grade() {
    }

    public static Grade ok(long answerId, BigDecimal overall, String summary, List<DimensionScore> dimensions,
                           List<KeyPointHit> keyPoints, List<String> strengths, List<String> improvements,
                           List<FollowUp> followUps, Usage usage) {
        Grade grade = new Grade(answerId, GradeStatus.OK, usage);
        grade.overall = overall;
        grade.summary = summary;
        grade.dimensions = new ArrayList<>(dimensions);
        grade.keyPoints = new ArrayList<>(keyPoints);
        grade.strengths = new ArrayList<>(strengths);
        grade.improvements = new ArrayList<>(improvements);
        grade.followUps = new ArrayList<>(followUps);
        grade.unverifiedQuotes = (int) dimensions.stream().filter(score -> !score.isEvidenceVerified()).count();
        return grade;
    }

    public static Grade followUp(long answerId, BigDecimal bonus, List<FollowUpResult> results, Usage usage) {
        Grade grade = new Grade(answerId, GradeStatus.OK, usage);
        grade.bonus = bonus;
        grade.followUpResults = new ArrayList<>(results);
        grade.unverifiedQuotes = (int) results.stream().filter(result -> !result.verified()).count();
        return grade;
    }

    public static Grade failed(long answerId, String reason, String detail, Usage usage) {
        Grade grade = new Grade(answerId, GradeStatus.FAILED, usage);
        grade.failureReason = reason;
        grade.failureDetail = detail;
        return grade;
    }

    private Grade(long answerId, GradeStatus status, Usage usage) {
        this.answerId = answerId;
        this.status = status;
        this.model = usage.model();
        this.promptTokens = usage.promptTokens();
        this.completionTokens = usage.completionTokens();
        this.latencyMs = usage.latencyMs();
        this.attempts = usage.attempts();
    }

    public record Usage(String model, int promptTokens, int completionTokens, long latencyMs, int attempts) {

        public static final Usage NONE = new Usage(null, 0, 0, 0, 0);

        public Usage plus(Usage other) {
            return new Usage(other.model != null ? other.model : model, promptTokens + other.promptTokens,
                    completionTokens + other.completionTokens, latencyMs + other.latencyMs,
                    attempts + other.attempts);
        }
    }

    public Long getId() {
        return id;
    }

    public long getAnswerId() {
        return answerId;
    }

    public GradeStatus getStatus() {
        return status;
    }

    public BigDecimal getOverall() {
        return overall;
    }

    public String getSummary() {
        return summary;
    }

    public BigDecimal getBonus() {
        return bonus;
    }

    public List<FollowUpResult> getFollowUpResults() {
        return List.copyOf(followUpResults);
    }

    public List<String> getStrengths() {
        return List.copyOf(strengths);
    }

    public List<String> getImprovements() {
        return List.copyOf(improvements);
    }

    public List<FollowUp> getFollowUps() {
        return List.copyOf(followUps);
    }

    public int getUnverifiedQuotes() {
        return unverifiedQuotes;
    }

    public String getModel() {
        return model;
    }

    public int getPromptTokens() {
        return promptTokens;
    }

    public int getCompletionTokens() {
        return completionTokens;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public String getFailureDetail() {
        return failureDetail;
    }

    public int getAttempts() {
        return attempts;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public List<DimensionScore> getDimensions() {
        return List.copyOf(dimensions);
    }

    public List<KeyPointHit> getKeyPoints() {
        return List.copyOf(keyPoints);
    }
}
