package dev.mosiur.drill.answer;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.assertions.LocatorAssertions.ContainsTextOptions;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.LoadState;
import dev.mosiur.drill.PostgresTestConfiguration;
import dev.mosiur.drill.support.WebSession;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PostgresTestConfiguration.class)
class EditorUiIT {

    private static Playwright playwright;
    private static Browser browser;

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    private BrowserContext context;
    private final List<String> consoleErrors = new ArrayList<>();

    @BeforeAll
    static void launchBrowser() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch();
    }

    @AfterAll
    static void closeBrowser() {
        playwright.close();
    }

    @BeforeEach
    void clearDrills() {
        jdbc.update("delete from answer");
        jdbc.update("delete from drill");
    }

    @AfterEach
    void closeContext() {
        context.close();
    }

    @Test
    void desktopEditorSavesDraftsDrawsTheDiagramAndSubmits() {
        Page page = signIn(1280, 900);
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Start answer")).click();
        page.waitForLoadState(LoadState.LOAD);

        assertThat(page.locator("[data-timer]")).containsText(Pattern.compile("\\d\\d:\\d\\d"));
        Locator requirements = page.getByLabel("1 Requirements");
        requirements.fill("Users shorten links and open them. Reads dominate writes.");
        assertThat(page.locator("[data-save-state]")).containsText("Saved", new ContainsTextOptions().setTimeout(10_000));
        assertThat(page.locator("[data-count-for=requirements]")).hasText("9 w");

        page.reload();
        assertThat(page.getByLabel("1 Requirements")).hasValue("Users shorten links and open them. Reads dominate writes.");

        page.getByLabel("5 Architecture").fill("flowchart LR\n  api[API] --> db[(Store)]");
        assertThat(page.locator("[data-diagram-preview] svg")).isVisible();

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Submit for grading")).click();
        Locator confirm = page.locator("[data-confirm]");
        assertThat(confirm).isVisible();
        assertThat(confirm).containsText("6 sections are still empty: Estimation, API, Data model, Deep dive, "
                + "Scale and failure, Trade-offs.");

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Keep writing")).click();
        assertThat(confirm).isHidden();

        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Submit for grading")).click();
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Submit anyway")).click();

        page.waitForURL(Pattern.compile(".*/drills/\\d+/result$"));
        assertThat(page.locator("h1")).isVisible();
        assertThat(page.locator("#grading-title, #failed-title")).isVisible();
        Assertions.assertThat(jdbc.queryForObject("select status from drill", String.class)).isEqualTo("GRADING");
        Assertions.assertThat(consoleErrors).isEmpty();
    }

    @Test
    void phoneEditorShowsOneSectionAtATime() {
        Page page = signIn(390, 844);
        page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Start answer")).click();
        page.waitForLoadState(LoadState.LOAD);

        Locator requirements = page.locator("#answer-requirements");
        Locator estimation = page.locator("#answer-estimation");
        assertThat(requirements).isVisible();
        assertThat(estimation).isHidden();
        assertThat(page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Submit").setExact(true)))
                .isVisible();

        page.getByLabel("Next section").click();

        assertThat(requirements).isHidden();
        assertThat(estimation).isVisible();
        assertThat(estimation).isFocused();
        assertThat(page.locator("[data-section-select]")).hasValue("estimation");

        page.locator("[data-section-select]").selectOption("architecture");
        assertThat(page.locator("#answer-architecture")).isVisible();
        Assertions.assertThat(consoleErrors).isEmpty();
    }

    private Page signIn(int width, int height) {
        context = browser.newContext(new Browser.NewContextOptions().setViewportSize(width, height));
        Page page = context.newPage();
        page.onConsoleMessage(message -> {
            if ("error".equals(message.type())) {
                consoleErrors.add(message.text());
            }
        });
        page.navigate("http://localhost:" + port + "/");
        assertThat(page).hasURL(Pattern.compile(".*/login$"));
        page.getByLabel("Username").fill(WebSession.USERNAME);
        page.getByLabel("Password").fill(WebSession.PASSWORD);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in")).click();
        assertThat(page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName("Start answer"))).isVisible();
        return page;
    }
}
