package dev.mosiur.drill.grading;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import com.github.tomakehurst.wiremock.WireMockServer;
import dev.mosiur.drill.PostgresTestConfiguration;
import dev.mosiur.drill.drill.model.TodayDrill;
import dev.mosiur.drill.drill.service.DailyDrillService;
import dev.mosiur.drill.support.WebSession;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestConfiguration.class)
class GradingIT {

    private static final WireMockServer GROQ = new WireMockServer(wireMockConfig().dynamicPort());

    static {
        GROQ.start();
    }

    @DynamicPropertySource
    static void groq(DynamicPropertyRegistry registry) {
        registry.add("drill.llm.base-url", GROQ::baseUrl);
        registry.add("drill.llm.api-key", () -> "gsk_test");
    }

    @AfterAll
    static void stopGroq() {
        GROQ.stop();
    }

    @LocalServerPort
    private int port;

    @Autowired
    private DailyDrillService dailyDrills;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private JsonMapper json;

    @BeforeEach
    void setUp() {
        GROQ.resetAll();
        jdbc.update("delete from answer");
        jdbc.update("delete from drill");
    }

    @Test
    void gradesASubmittedAnswerInTheBackground() {
        WebSession session = WebSession.signedIn(port);
        TodayDrill drill = dailyDrills.assignToday().orElseThrow();
        String uri = "/drills/" + drill.id();
        stubGrade(drill.id());
        session.page(uri);

        session.post(uri + "/submit", answer()).expectStatus().isSeeOther();

        await().atMost(Duration.ofSeconds(10)).until(() -> "GRADED".equals(status(drill.id())));
        String page = session.page(uri + "/result");
        assertThat(page).contains("<span class=\"score-value\">50</span>")
                .contains("Clear API, thin on failure handling.")
                .contains("Improve these first").contains("Add a cache for hot links.")
                .contains("“Users shorten long links”")
                .contains("Checklist").doesNotContain("This quote was not found");
        assertThat(jdbc.queryForObject("select prompt_tokens from grade", Integer.class)).isEqualTo(120);
        assertThat(jdbc.queryForObject("select count(*) from dimension_score", Integer.class)).isEqualTo(8);
        session.get(uri + "/result/progress").expectHeader().valueEquals("HX-Refresh", "true");
        GROQ.verify(1, postRequestedFor(urlEqualTo("/chat/completions")));
    }

    @Test
    void showsAFailureAndGradesAgainOnRequest() {
        WebSession session = WebSession.signedIn(port);
        TodayDrill drill = dailyDrills.assignToday().orElseThrow();
        String uri = "/drills/" + drill.id();
        GROQ.stubFor(post("/chat/completions").willReturn(aResponse().withStatus(401)
                .withBody("{\"error\": {\"message\": \"Invalid API Key\"}}")));
        session.page(uri);

        session.post(uri + "/submit", answer()).expectStatus().isSeeOther();

        await().atMost(Duration.ofSeconds(10)).until(() -> gradeStatus(drill.id()) != null);
        assertThat(gradeStatus(drill.id())).isEqualTo("FAILED");
        String failed = session.page(uri + "/result");
        assertThat(failed).contains("Grading didn't finish").contains("rejected the request");

        stubGrade(drill.id());
        session.post(uri + "/regrade", new LinkedMultiValueMap<>()).expectStatus().isSeeOther();

        await().atMost(Duration.ofSeconds(10)).until(() -> "GRADED".equals(status(drill.id())));
        assertThat(gradeStatus(drill.id())).isEqualTo("OK");
        assertThat(jdbc.queryForObject("select count(*) from grade", Integer.class)).isEqualTo(1);
    }

    @Test
    void answeredFollowUpsAddToTheFinalScore() {
        WebSession session = WebSession.signedIn(port);
        String uri = gradedDrill(session);
        assertThat(session.page(uri + "/result")).contains("Follow-up questions")
                .contains("1. What happens when the database is down?").contains("Skip and finish");
        stubFollowUpGrade();
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("follow_up_1", "If the primary is down, replicas take over reads and writes queue.");
        form.add("follow_up_2", "Each server leases ranges of IDs from a counter.");

        session.post(uri + "/follow-up", form).expectStatus().isSeeOther();

        long drillId = drillId(uri);
        await().atMost(Duration.ofSeconds(10)).until(() -> "DONE".equals(status(drillId)));
        assertThat(finalScore(drillId)).isEqualByComparingTo("55.00");
        assertThat(session.page(uri + "/result")).contains("<span class=\"score-value\">55</span>")
                .contains("50 for the answer + 5 from follow-ups")
                .contains("replicas take over reads").contains("Same depth as before.");
        session.post(uri + "/follow-up", form).expectStatus().isEqualTo(409);
    }

    @Test
    void skippingFollowUpsKeepsTheMainScore() {
        WebSession session = WebSession.signedIn(port);
        String uri = gradedDrill(session);

        session.post(uri + "/finish", new LinkedMultiValueMap<>()).expectStatus().isSeeOther();

        assertThat(status(drillId(uri))).isEqualTo("DONE");
        assertThat(finalScore(drillId(uri))).isEqualByComparingTo("50.00");
        assertThat(session.page(uri + "/result")).contains("You skipped the follow-ups.")
                .doesNotContain("Submit follow-up");
    }

    @Test
    void pollingAsksForNothingWhileGrading() {
        WebSession session = WebSession.signedIn(port);
        TodayDrill drill = dailyDrills.assignToday().orElseThrow();
        GROQ.stubFor(post("/chat/completions").willReturn(aResponse().withFixedDelay(3_000).withStatus(503)));
        session.page("/drills/" + drill.id());
        session.post("/drills/" + drill.id() + "/submit", answer()).expectStatus().isSeeOther();

        assertThat(session.page("/drills/" + drill.id() + "/result")).contains("Grading your answer")
                .contains("hx-trigger=\"every 2s\"");
        session.get("/drills/" + drill.id() + "/result/progress").expectStatus().isNoContent()
                .expectHeader().doesNotExist("HX-Refresh");
    }

    private void stubGrade(long drillId) {
        String content = GradingFixtures.gradeJson(Map.of(), Map.of());
        String codes = jdbc.queryForList("""
                select k.code from key_point k join drill d on d.problem_id = k.problem_id
                where d.id = ? and k.active""", String.class, drillId).stream()
                .map(code -> "{\"code\": \"" + code + "\", \"covered\": true}")
                .reduce((a, b) -> a + ", " + b).orElse("");
        String grade = content.replaceFirst("\"key_points\": \\[[^]]*]", "\"key_points\": [" + codes + "]");
        String body = json.writeValueAsString(Map.of(
                "model", "openai/gpt-oss-120b",
                "choices", List.of(Map.of("index", 0, "finish_reason", "stop",
                        "message", Map.of("role", "assistant", "content", grade))),
                "usage", Map.of("prompt_tokens", 120, "completion_tokens", 30)));
        GROQ.stubFor(post("/chat/completions")
                .withRequestBody(matchingJsonPath("$.response_format.json_schema.name", equalTo("system_design_grade")))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body)));
    }

    private void stubFollowUpGrade() {
        String content = """
                {"answers": [
                  {"question": 1, "score": 4, "evidence": "replicas take over reads", "feedback": "Clear."},
                  {"question": 2, "score": 2, "evidence": "ranges of ids", "feedback": "Same depth as before."}]}
                """;
        String body = json.writeValueAsString(Map.of(
                "model", "openai/gpt-oss-120b",
                "choices", List.of(Map.of("index", 0, "finish_reason", "stop",
                        "message", Map.of("role", "assistant", "content", content))),
                "usage", Map.of("prompt_tokens", 80, "completion_tokens", 20)));
        GROQ.stubFor(post("/chat/completions")
                .withRequestBody(matchingJsonPath("$.response_format.json_schema.name", equalTo("follow_up_grade")))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody(body)));
    }

    private String gradedDrill(WebSession session) {
        TodayDrill drill = dailyDrills.assignToday().orElseThrow();
        String uri = "/drills/" + drill.id();
        stubGrade(drill.id());
        session.page(uri);
        session.post(uri + "/submit", answer()).expectStatus().isSeeOther();
        await().atMost(Duration.ofSeconds(10)).until(() -> "GRADED".equals(status(drill.id())));
        return uri;
    }

    private long drillId(String uri) {
        return Long.parseLong(uri.substring(uri.lastIndexOf('/') + 1));
    }

    private BigDecimal finalScore(long drillId) {
        return jdbc.queryForObject("select final_score from drill where id = ?", BigDecimal.class, drillId);
    }

    private static MultiValueMap<String, String> answer() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        GradingFixtures.SECTIONS.forEach(form::add);
        return form;
    }

    private String status(long drillId) {
        return jdbc.queryForObject("select status from drill where id = ?", String.class, drillId);
    }

    private String gradeStatus(long drillId) {
        return jdbc.queryForList("""
                select g.status from grade g join answer a on a.id = g.answer_id where a.drill_id = ?""",
                String.class, drillId).stream().findFirst().orElse(null);
    }
}
