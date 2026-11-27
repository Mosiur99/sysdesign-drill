package dev.mosiur.drill.drill;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.IntStream;

import dev.mosiur.drill.PostgresTestConfiguration;
import dev.mosiur.drill.drill.domain.DrillKind;
import dev.mosiur.drill.drill.domain.DrillStatus;
import dev.mosiur.drill.drill.model.TodayDrill;
import dev.mosiur.drill.drill.service.DailyDrillService;
import dev.mosiur.drill.problem.domain.Problem;
import dev.mosiur.drill.problem.repository.ProblemRepository;
import dev.mosiur.drill.support.WebSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestConfiguration.class)
class DailyDrillIT {

    @LocalServerPort
    private int port;

    @Autowired
    private DailyDrillService dailyDrills;

    @Autowired
    private ProblemRepository problems;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from answer");
        jdbc.update("delete from drill");
        jdbc.update("delete from problem_progress");
    }

    @Test
    void assignsANewMediumProblemOnTheFirstDay() {
        TodayDrill drill = dailyDrills.assignToday().orElseThrow();

        assertThat(drill.day()).isEqualTo(LocalDate.now(clock));
        assertThat(drill.kind()).isEqualTo(DrillKind.NEW);
        assertThat(drill.status()).isEqualTo(DrillStatus.ASSIGNED);
        assertThat(drill.difficulty()).isEqualTo(2);
    }

    @Test
    void assigningAgainReturnsTheSameDrill() {
        TodayDrill first = dailyDrills.assignToday().orElseThrow();

        assertThat(dailyDrills.assignToday()).contains(first);
        assertThat(drillCount()).isEqualTo(1);
    }

    @Test
    void assignsADueRetry() {
        Problem payments = problemBySlug("payment-ledger");
        LocalDate today = LocalDate.now(clock);
        jdbc.update("""
                insert into problem_progress (problem_id, attempts, best_score, last_score, last_attempted_on, next_due)
                values (?, 1, 48, 48, ?, ?)
                """, payments.getId(), today.minusDays(4), today.minusDays(1));

        TodayDrill drill = dailyDrills.assignToday().orElseThrow();

        assertThat(drill.kind()).isEqualTo(DrillKind.RETRY);
        assertThat(drill.title()).isEqualTo(payments.getTitle());
    }

    @Test
    void concurrentRequestsCreateExactlyOneDrill() throws Exception {
        int threads = 8;
        CountDownLatch start = new CountDownLatch(1);
        Callable<TodayDrill> assign = () -> {
            start.await();
            return dailyDrills.assignToday().orElseThrow();
        };
        try (ExecutorService pool = Executors.newFixedThreadPool(threads)) {
            List<Future<TodayDrill>> results = IntStream.range(0, threads).mapToObj(i -> pool.submit(assign)).toList();
            start.countDown();

            List<Long> ids = results.stream().map(DailyDrillIT::await).map(TodayDrill::id).distinct().toList();

            assertThat(ids).hasSize(1);
        }
        assertThat(drillCount()).isEqualTo(1);
    }

    @Test
    void todayPageShowsTheAssignedProblem() {
        String html = WebSession.signedIn(port).page("/");

        TodayDrill drill = dailyDrills.assignToday().orElseThrow();
        assertThat(html)
                .contains("<h1 id=\"problem-title\">" + drill.title() + "</h1>")
                .contains("New problem")
                .contains("Medium")
                .contains("href=\"/drills/" + drill.id() + "\"");
    }

    @Test
    void problemsPageListsTheActiveBank() {
        String html = WebSession.signedIn(port).page("/problems");

        int active = problems.findByActiveTrue().size();
        assertThat(html).contains(active + " problems, easiest first").contains("Design a URL shortener");
        assertThat(html.split("<tr>", -1)).hasSize(active + 2);
    }

    private Problem problemBySlug(String slug) {
        return problems.findAll().stream().filter(problem -> problem.getSlug().equals(slug)).findFirst().orElseThrow();
    }

    private int drillCount() {
        return jdbc.queryForObject("select count(*) from drill", Integer.class);
    }

    private static <T> T await(Future<T> future) {
        try {
            return future.get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
