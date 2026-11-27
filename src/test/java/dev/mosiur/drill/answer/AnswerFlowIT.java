package dev.mosiur.drill.answer;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.regex.Pattern;

import dev.mosiur.drill.PostgresTestConfiguration;
import dev.mosiur.drill.drill.model.TodayDrill;
import dev.mosiur.drill.drill.service.DailyDrillService;
import dev.mosiur.drill.support.WebSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestConfiguration.class)
class AnswerFlowIT {

    @LocalServerPort
    private int port;

    @Autowired
    private DailyDrillService dailyDrills;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        jdbc.update("delete from answer");
        jdbc.update("delete from drill");
    }

    @Test
    void anonymousVisitorsAreSentToSignIn() {
        WebSession.anonymous(port).get("/")
                .expectStatus().is3xxRedirection()
                .expectHeader().valueMatches("Location", ".*/login$");
    }

    @Test
    void wrongPasswordSaysHowManyTriesAreLeft() {
        WebSession session = WebSession.anonymous(port);
        session.page("/login");

        session.post("/login", form(Map.of("username", WebSession.USERNAME, "password", "wrong")))
                .expectStatus().is3xxRedirection()
                .expectHeader().valueMatches("Location", ".*" + Pattern.quote("/login?error"));

        assertThat(session.page("/login?error")).contains("Username or password is wrong. 4 tries left this minute.");
        WebSession.signedIn(port);
    }

    @Test
    void writesSavesAndSubmitsAnAnswer() {
        WebSession session = WebSession.signedIn(port);
        TodayDrill drill = dailyDrills.assignToday().orElseThrow();
        String editorUri = "/drills/" + drill.id();

        assertThat(session.page("/")).contains("Start answer");
        String editor = session.page(editorUri);
        assertThat(editor).contains(drill.title()).contains("1 Requirements").contains("8 Trade-offs");
        assertThat(status(drill.id())).isEqualTo("IN_PROGRESS");

        session.put(editorUri + "/answer", form(Map.of("requirements", "Shorten long links", "api", "POST /links")))
                .expectStatus().isNoContent();
        assertThat(jdbc.queryForObject("select word_count from answer where drill_id = ?", Integer.class, drill.id()))
                .isEqualTo(5);
        assertThat(session.page(editorUri)).contains("Shorten long links");
        assertThat(session.page("/")).contains("Continue answer");

        session.post(editorUri + "/submit", form(Map.of("requirements", "Shorten long links fast")))
                .expectStatus().isSeeOther()
                .expectHeader().valueMatches("Location", ".*" + Pattern.quote(editorUri + "/result"));

        assertThat(status(drill.id())).isEqualTo("GRADING");
        assertThat(jdbc.queryForObject("select draft from answer where drill_id = ?", Boolean.class, drill.id()))
                .isFalse();
        assertThat(session.page(editorUri + "/result")).contains("Your answer is submitted").contains("4 words");
        assertThat(session.page("/")).contains("See result");

        session.post(editorUri + "/submit", form(Map.of())).expectStatus().isEqualTo(409);
        session.put(editorUri + "/answer", form(Map.of("api", "changed"))).expectStatus().isEqualTo(409);
        session.get(editorUri).expectStatus().is3xxRedirection()
                .expectHeader().valueMatches("Location", ".*" + Pattern.quote(editorUri + "/result"));
    }

    @Test
    void rejectsWritesWithoutTheCsrfToken() {
        WebSession session = WebSession.signedIn(port);
        TodayDrill drill = dailyDrills.assignToday().orElseThrow();

        session.withoutCsrf().put("/drills/" + drill.id() + "/answer", form(Map.of("api", "x")))
                .expectStatus().isForbidden();
    }

    @Test
    void unknownDrillIsNotFound() {
        WebSession.signedIn(port).get("/drills/999999").expectStatus().isNotFound();
    }

    private String status(long drillId) {
        return jdbc.queryForObject("select status from drill where id = ?", String.class, drillId);
    }

    private static MultiValueMap<String, String> form(Map<String, String> values) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        values.forEach(form::add);
        return form;
    }
}
