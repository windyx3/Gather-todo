package com.windy.todo;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties =
        "spring.datasource.url=jdbc:h2:mem:console-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1")
@ActiveProfiles("local")
class H2ConsoleIntegrationTest {
    @LocalServerPort int port;
    final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build();

    HttpResponse<String> get(String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test void consoleCanLoginAndQueryWhileApiStillRequiresAuthenticationAndCsrf() throws Exception {
        var loginPage = get("/h2-console/");
        assertThat(loginPage.statusCode()).isEqualTo(200);
        assertThat(loginPage.headers().firstValue("X-Frame-Options")).contains("SAMEORIGIN");
        var session = Pattern.compile("jsessionid=([a-zA-Z0-9]+)").matcher(loginPage.body());
        assertThat(session.find()).isTrue();
        String sessionId = session.group(1);
        String jdbc = "jdbc:h2:mem:console-test;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;IFEXISTS=TRUE";
        var login = post("/h2-console/login.do?jsessionid=" + sessionId,
                "driver=org.h2.Driver&url=" + encode(jdbc) + "&user=sa&password=");
        assertThat(login.statusCode()).isEqualTo(200);
        assertThat(login.body()).contains("name=\"h2query\"");
        var query = post("/h2-console/query.do?jsessionid=" + sessionId,
                "sql=" + encode("SELECT COUNT(*) AS account_count FROM app_users WHERE role = 'USER'"));
        assertThat(query.statusCode()).isEqualTo(200);
        assertThat(query.body()).containsIgnoringCase("account_count").contains("<td>0</td>");
        assertThat(get("/h2-console/logout.do?jsessionid=" + sessionId).statusCode()).isEqualTo(200);
        assertThat(get("/api/projects").statusCode()).isEqualTo(401);
        assertThat(post("/api/auth/logout", "").statusCode()).isEqualTo(403);
        assertThat(get("/api/auth/csrf").headers().firstValue("X-Frame-Options")).contains("DENY");
    }

    HttpResponse<String> post(String path, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
