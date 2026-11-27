package dev.mosiur.drill.notify;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import dev.mosiur.drill.PostgresTestConfiguration;
import dev.mosiur.drill.drill.model.TodayDrill;
import dev.mosiur.drill.drill.service.DailyDrillService;
import dev.mosiur.drill.notify.domain.NotificationKind;
import dev.mosiur.drill.notify.repository.NotificationRepository;
import dev.mosiur.drill.notify.service.impl.NotificationSender;
import dev.mosiur.drill.notify.service.impl.NotifyScheduler;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@Import(PostgresTestConfiguration.class)
class OutboxIT {

    private static final WireMockServer TELEGRAM = new WireMockServer(wireMockConfig().dynamicPort());
    private static final String PATH = "/bottest-token/sendMessage";

    static {
        TELEGRAM.start();
    }

    @DynamicPropertySource
    static void telegram(DynamicPropertyRegistry registry) {
        registry.add("drill.telegram.base-url", TELEGRAM::baseUrl);
        registry.add("drill.telegram.bot-token", () -> "test-token");
        registry.add("drill.telegram.chat-id", () -> "42");
        registry.add("drill.telegram.app-url", () -> "https://drill.example");
    }

    @AfterAll
    static void stopTelegram() {
        TELEGRAM.stop();
    }

    @Autowired
    private NotificationRepository notifications;

    @Autowired
    private NotificationSender sender;

    @Autowired
    private NotifyScheduler scheduler;

    @Autowired
    private DailyDrillService dailyDrills;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        TELEGRAM.resetAll();
        jdbc.update("delete from notification");
        jdbc.update("delete from answer");
        jdbc.update("delete from drill");
    }

    @Test
    void queuesEachMessageOnceAndSendsIt() {
        stub(ok());

        assertThat(notifications.enqueue(NotificationKind.DAILY, "daily:x", "hello", Instant.now())).isTrue();
        assertThat(notifications.enqueue(NotificationKind.DAILY, "daily:x", "hello again", Instant.now())).isFalse();
        sender.sendDue();
        sender.sendDue();

        assertThat(status("daily:x")).isEqualTo("SENT");
        TELEGRAM.verify(1, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void waitsForRetryAfterThenSends() {
        TELEGRAM.stubFor(post(PATH).inScenario("rate").whenScenarioStateIs(Scenario.STARTED)
                .willReturn(aResponse().withStatus(429).withBody(
                        "{\"ok\": false, \"description\": \"Too Many Requests\", \"parameters\": {\"retry_after\": 30}}"))
                .willSetStateTo("ok"));
        TELEGRAM.stubFor(post(PATH).inScenario("rate").whenScenarioStateIs("ok").willReturn(ok()));
        notifications.enqueue(NotificationKind.DAILY, "daily:y", "hello", Instant.now());

        sender.sendDue();
        assertThat(status("daily:y")).isEqualTo("PENDING");
        sender.sendDue();
        TELEGRAM.verify(1, postRequestedFor(urlEqualTo(PATH)));

        jdbc.update("update notification set next_attempt_at = now() where dedupe_key = 'daily:y'");
        sender.sendDue();

        assertThat(status("daily:y")).isEqualTo("SENT");
        assertThat(jdbc.queryForObject("select attempts from notification", Integer.class)).isEqualTo(2);
    }

    @Test
    void neverRetriesAClientError() {
        stub(aResponse().withStatus(400).withBody("{\"ok\": false, \"description\": \"Bad Request: can't parse\"}"));
        notifications.enqueue(NotificationKind.WEEKLY, "weekly:z", "<b>broken", Instant.now());

        sender.sendDue();

        assertThat(status("weekly:z")).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select last_error from notification", String.class))
                .isEqualTo("400 Bad Request: can't parse");
    }

    @Test
    void givesUpAfterThreeAttempts() {
        stub(aResponse().withStatus(502));
        notifications.enqueue(NotificationKind.DAILY, "daily:w", "hello", Instant.now());

        for (int i = 0; i < 3; i++) {
            jdbc.update("update notification set next_attempt_at = now() - interval '1 second'");
            sender.sendDue();
        }

        assertThat(status("daily:w")).isEqualTo("FAILED");
        TELEGRAM.verify(3, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void twoSendersRunningTogetherSendOnlyOnce() throws Exception {
        stub(ok().withFixedDelay(500));
        notifications.enqueue(NotificationKind.DAILY, "daily:v", "hello", Instant.now());
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            List<Future<?>> runs = List.of(pool.submit(() -> run(start)), pool.submit(() -> run(start)));
            start.countDown();
            for (Future<?> run : runs) {
                run.get();
            }
        }

        assertThat(status("daily:v")).isEqualTo("SENT");
        TELEGRAM.verify(1, postRequestedFor(urlEqualTo(PATH)));
    }

    @Test
    void schedulerQueuesTheDailyProblemAndARemindersOnlyBeforeSubmitting() {
        TodayDrill drill = dailyDrills.assignToday().orElseThrow();

        scheduler.daily();
        scheduler.daily();
        scheduler.reminder();

        assertThat(jdbc.queryForObject("select count(*) from notification", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select payload from notification where kind = 'DAILY'", String.class))
                .contains(drill.title().replace("&", "&amp;"))
                .contains("https://drill.example/drills/" + drill.id());

        jdbc.update("delete from notification");
        jdbc.update("update drill set status = 'GRADING', submitted_at = now() where id = ?", drill.id());
        scheduler.reminder();
        scheduler.weekly();

        assertThat(jdbc.queryForList("select kind from notification", String.class)).containsExactly("WEEKLY");
    }

    private void run(CountDownLatch start) {
        try {
            start.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        sender.sendDue();
    }

    private void stub(ResponseDefinitionBuilder response) {
        TELEGRAM.stubFor(post(PATH).willReturn(response));
    }

    private static ResponseDefinitionBuilder ok() {
        return aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("{\"ok\": true}");
    }

    private String status(String dedupeKey) {
        return jdbc.queryForObject("select status from notification where dedupe_key = ?", String.class, dedupeKey);
    }
}
