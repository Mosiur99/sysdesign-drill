package dev.mosiur.drill.progress;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import dev.mosiur.drill.PostgresTestConfiguration;
import dev.mosiur.drill.drill.domain.DrillKind;
import dev.mosiur.drill.drill.model.TodayDrill;
import dev.mosiur.drill.drill.service.DailyDrillService;
import dev.mosiur.drill.support.MutableClock;
import dev.mosiur.drill.support.WebSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({PostgresTestConfiguration.class, ProgressIT.ClockConfig.class})
class ProgressIT {

    private static final LocalDate START = LocalDate.of(2026, 12, 1);

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfig {

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(Instant.parse("2026-12-01T03:00:00Z"), ZoneId.of("Asia/Dhaka"));
        }
    }

    @LocalServerPort
    private int port;

    @Autowired
    private MutableClock clock;

    @Autowired
    private DailyDrillService dailyDrills;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from answer");
        jdbc.update("delete from drill");
        jdbc.update("delete from problem_progress");
        clock.setDay(START);
    }

    @Test
    void aWeakScoreComesBackAsARetryThreeDaysLater() {
        TodayDrill first = dailyDrills.assignToday().orElseThrow();
        grade(first.id(), 40);

        clock.setDay(START.plusDays(1));
        TodayDrill second = dailyDrills.assignToday().orElseThrow();

        assertThat(status(first.id())).isEqualTo("DONE");
        assertThat(jdbc.queryForObject("select next_due from problem_progress where problem_id = ?",
                LocalDate.class, problemId(first.id()))).isEqualTo(START.plusDays(3));
        assertThat(second.kind()).isEqualTo(DrillKind.NEW);
        assertThat(second.title()).isNotEqualTo(first.title());

        grade(second.id(), 80);
        clock.setDay(START.plusDays(2));
        assertThat(dailyDrills.assignToday().orElseThrow().kind()).isEqualTo(DrillKind.NEW);

        clock.setDay(START.plusDays(3));
        TodayDrill retry = dailyDrills.assignToday().orElseThrow();

        assertThat(retry.kind()).isEqualTo(DrillKind.RETRY);
        assertThat(retry.title()).isEqualTo(first.title());
        assertThat(WebSession.signedIn(port).page("/drills/" + first.id() + "/result"))
                .contains("Retry scheduled for <strong>Fri 4 Dec</strong>.");
    }

    @Test
    void progressPagesShowRealData() {
        for (int day = 0; day < 3; day++) {
            clock.setDay(START.plusDays(day));
            TodayDrill drill = dailyDrills.assignToday().orElseThrow();
            grade(drill.id(), 50 + day * 10);
        }
        clock.setDay(START.plusDays(3));
        dailyDrills.assignToday();
        WebSession session = WebSession.signedIn(port);

        String progress = session.page("/progress");
        String today = session.page("/");

        assertThat(progress).contains("<strong>3 drills</strong>").contains("Average score <strong>60</strong>")
                .contains("Longest streak <span>3 days</span>").contains("points=\"0.0,")
                .contains("Topics").contains("not tried").contains("Recent drills");
        assertThat(today).contains("3-day streak").contains("This week").contains("Where to focus")
                .contains(">50<").contains(">60<").contains(">70<").contains("to do")
                .contains("Next retry:");
    }

    @Test
    void progressExplainsWhenThereIsNotEnoughData() {
        assertThat(WebSession.signedIn(port).page("/progress")).contains("Trends start after 3 drills");
    }

    private void grade(long drillId, int overall) {
        jdbc.update("""
                insert into answer (drill_id, round, sections, word_count, seconds_spent, draft, updated_at)
                values (?, 1, '{"requirements": "x"}', 1, 600, false, now())""", drillId);
        Long gradeId = jdbc.queryForObject("""
                insert into grade (answer_id, status, overall, summary, improvements, model, attempts)
                select id, 'OK', ?, 'ok', '["a"]', 'test', 1 from answer where drill_id = ? and round = 1
                returning id""", Long.class, BigDecimal.valueOf(overall), drillId);
        for (String dimension : new String[] {"requirements", "estimation", "api", "data_model", "architecture",
                "deep_dive", "scale_failure", "tradeoffs"}) {
            jdbc.update("""
                    insert into dimension_score (grade_id, dimension, score, evidence, evidence_verified)
                    values (?, ?, ?, 'x', true)""", gradeId, dimension, overall / 25);
        }
        jdbc.update("update drill set status = 'GRADED', started_at = now(), submitted_at = now() where id = ?",
                drillId);
    }

    private String status(long drillId) {
        return jdbc.queryForObject("select status from drill where id = ?", String.class, drillId);
    }

    private long problemId(long drillId) {
        return jdbc.queryForObject("select problem_id from drill where id = ?", Long.class, drillId);
    }
}
