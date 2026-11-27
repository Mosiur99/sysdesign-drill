package dev.mosiur.drill.support;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.web.servlet.client.RestTestClient;
import org.springframework.test.web.servlet.client.RestTestClient.ResponseSpec;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

public final class WebSession {

    public static final String USERNAME = "tester";
    public static final String PASSWORD = "test-password";

    private static final Pattern CSRF = Pattern.compile("name=\"_csrf\" content=\"([^\"]+)\"|name=\"_csrf\" value=\"([^\"]+)\"");

    private final RestTestClient client;
    private String sessionCookie = "";
    private String csrf;

    private WebSession(int port) {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    public static WebSession anonymous(int port) {
        return new WebSession(port);
    }

    public static WebSession signedIn(int port) {
        WebSession session = new WebSession(port);
        session.page("/login");
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("username", USERNAME);
        form.add("password", PASSWORD);
        session.post("/login", form).expectStatus().is3xxRedirection()
                .expectHeader().valueMatches("Location", ".*:\\d+/$");
        return session;
    }

    public ResponseSpec get(String uri) {
        return remember(client.get().uri(uri).header(HttpHeaders.COOKIE, sessionCookie).exchange());
    }

    public String page(String uri) {
        String html = get(uri).expectStatus().isOk().expectBody(String.class).returnResult().getResponseBody();
        Matcher matcher = CSRF.matcher(html);
        if (matcher.find()) {
            csrf = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
        }
        return html;
    }

    public WebSession withoutCsrf() {
        csrf = "";
        return this;
    }

    public ResponseSpec post(String uri, MultiValueMap<String, String> form) {
        return send(client.post().uri(uri), form);
    }

    public ResponseSpec put(String uri, MultiValueMap<String, String> form) {
        return send(client.put().uri(uri), form);
    }

    private ResponseSpec send(RestTestClient.RequestBodySpec request, MultiValueMap<String, String> form) {
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>(form);
        if (csrf != null && !csrf.isEmpty()) {
            body.add("_csrf", csrf);
        }
        return remember(request
                .header(HttpHeaders.COOKIE, sessionCookie)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(body)
                .exchange());
    }

    private ResponseSpec remember(ResponseSpec response) {
        ResponseCookie session = response.returnResult().getResponseCookies().getFirst("JSESSIONID");
        if (session != null) {
            sessionCookie = "JSESSIONID=" + session.getValue();
        }
        return response;
    }
}
