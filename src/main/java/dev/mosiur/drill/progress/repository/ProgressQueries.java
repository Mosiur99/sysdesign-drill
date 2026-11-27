package dev.mosiur.drill.progress.repository;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import dev.mosiur.drill.problem.domain.Topic;
import dev.mosiur.drill.progress.model.NextRetry;
import dev.mosiur.drill.progress.model.ScoredDrill;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProgressQueries {

    private final JdbcTemplate jdbc;

    public ProgressQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ScoredDrill> scoredDrills() {
        Map<Long, Set<Topic>> topics = topicsByProblem(false);
        Map<Long, Map<String, Integer>> sections = new HashMap<>();
        jdbc.query("""
                select a.drill_id, ds.dimension, ds.score
                from dimension_score ds
                join grade g on g.id = ds.grade_id and g.status = 'OK'
                join answer a on a.id = g.answer_id and a.round = 1
                """, row -> {
            sections.computeIfAbsent(row.getLong(1), id -> new HashMap<>()).put(row.getString(2), row.getInt(3));
        });
        return jdbc.query("""
                select d.id, d.scheduled_for, d.kind, d.problem_id, p.title, coalesce(d.final_score, g.overall)
                from drill d
                join problem p on p.id = d.problem_id
                join answer a on a.drill_id = d.id and a.round = 1
                join grade g on g.answer_id = a.id and g.status = 'OK'
                order by d.scheduled_for, d.id
                """, (row, index) -> new ScoredDrill(row.getLong(1), row.getObject(2, LocalDate.class),
                "RETRY".equals(row.getString(3)), row.getLong(4), row.getString(5), row.getBigDecimal(6),
                topics.getOrDefault(row.getLong(4), Set.of()), sections.getOrDefault(row.getLong(1), Map.of())));
    }

    public Set<LocalDate> submittedDays() {
        return Set.copyOf(jdbc.queryForList(
                "select scheduled_for from drill where submitted_at is not null", LocalDate.class));
    }

    public Set<Topic> activeTopics() {
        Set<Topic> topics = EnumSet.noneOf(Topic.class);
        topicsByProblem(true).values().forEach(topics::addAll);
        return topics;
    }

    public Optional<NextRetry> nextRetry() {
        return jdbc.query("""
                select p.title, pp.next_due, pp.last_score
                from problem_progress pp join problem p on p.id = pp.problem_id
                where not pp.retired and pp.next_due is not null and p.active
                order by pp.next_due, pp.last_score
                limit 1
                """, (row, index) -> new NextRetry(row.getString(1), row.getObject(2, LocalDate.class),
                row.getBigDecimal(3))).stream().findFirst();
    }

    private Map<Long, Set<Topic>> topicsByProblem(boolean activeOnly) {
        Map<Long, Set<Topic>> topics = new HashMap<>();
        jdbc.query("select pt.problem_id, pt.topic from problem_topic pt join problem p on p.id = pt.problem_id"
                + (activeOnly ? " where p.active" : ""), row -> {
            topics.computeIfAbsent(row.getLong(1), id -> EnumSet.noneOf(Topic.class))
                    .add(Topic.valueOf(row.getString(2)));
        });
        return topics;
    }
}
