package dev.mosiur.drill.problem.domain;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "problem")
public class Problem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private String slug;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String prompt;

    @Column(nullable = false)
    private short difficulty;

    @Column(nullable = false)
    private boolean active = true;

    @ElementCollection
    @CollectionTable(name = "problem_topic", joinColumns = @JoinColumn(name = "problem_id"))
    @Column(name = "topic", nullable = false)
    @Enumerated(EnumType.STRING)
    private Set<Topic> topics = EnumSet.noneOf(Topic.class);

    @OneToMany(mappedBy = "problem", cascade = CascadeType.ALL)
    private List<KeyPoint> keyPoints = new ArrayList<>();

    protected Problem() {
    }

    public Problem(String slug) {
        this.slug = slug;
    }

    public void update(String title, String prompt, int difficulty, Set<Topic> topics) {
        this.title = title;
        this.prompt = prompt;
        this.difficulty = (short) difficulty;
        this.topics.clear();
        this.topics.addAll(topics);
        this.active = true;
    }

    public void putKeyPoint(String code, Dimension dimension, String text) {
        findKeyPoint(code).ifPresentOrElse(
                keyPoint -> keyPoint.update(dimension, text),
                () -> keyPoints.add(new KeyPoint(this, code, dimension, text)));
    }

    public void retainKeyPoints(Set<String> codes) {
        keyPoints.stream()
                .filter(keyPoint -> !codes.contains(keyPoint.getCode()))
                .forEach(KeyPoint::deactivate);
    }

    public void deactivate() {
        active = false;
    }

    public List<KeyPoint> activeKeyPoints() {
        return keyPoints.stream().filter(KeyPoint::isActive).toList();
    }

    private Optional<KeyPoint> findKeyPoint(String code) {
        return keyPoints.stream().filter(keyPoint -> keyPoint.getCode().equals(code)).findFirst();
    }

    public Long getId() {
        return id;
    }

    public String getSlug() {
        return slug;
    }

    public String getTitle() {
        return title;
    }

    public String getPrompt() {
        return prompt;
    }

    public int getDifficulty() {
        return difficulty;
    }

    public boolean isActive() {
        return active;
    }

    public Set<Topic> getTopics() {
        return Set.copyOf(topics);
    }
}
