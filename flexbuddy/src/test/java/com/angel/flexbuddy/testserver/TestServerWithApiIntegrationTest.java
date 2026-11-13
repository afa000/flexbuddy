package com.angel.flexbuddy.testserver;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import com.angel.flexbuddy.api.ApiAuthController;

/** The hosted test server as it will run: both switches on. The API chain's early answers carry noindex too. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "flexbuddy.test-server=true",
        "flexbuddy.api.enabled=true",
        "spring.datasource.url=jdbc:h2:mem:testserverapi;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test")
class TestServerWithApiIntegrationTest {

    @LocalServerPort int port;
    @Autowired ApplicationContext context;

    @Test
    void apiResponsesKeepTheirStatusAndBodyAndGainNoindex() throws Exception {
        assertThat(context.getBeansOfType(ApiAuthController.class)).hasSize(1);
        assertThat(context.containsBean("apiSecurityFilterChain")).isTrue();
        TestServerHttp http = new TestServerHttp(port);

        HttpResponse<String> unauthorized = http.get("/api/v1/account");
        assertThat(unauthorized.statusCode()).isEqualTo(401);
        assertThat(unauthorized.body()).isEqualTo("{\"error\":\"unauthorized\"}");
        assertThat(unauthorized.headers().firstValue("Content-Type").orElseThrow()).startsWith("application/json");
        assertThat(unauthorized.headers().allValues(TestServerHttp.HEADER)).containsExactly("noindex");

        HttpResponse<String> badToken = http.get("/api/v1/account", "Authorization", "Bearer not-a-real-token");
        assertThat(badToken.statusCode()).isEqualTo(401);
        assertThat(badToken.headers().allValues(TestServerHttp.HEADER)).containsExactly("noindex");

        // The API chain has no CSRF; an empty sign-in is a 400 from the controller, not a page.
        HttpResponse<String> emptySignIn = http.send("POST", "/api/v1/auth/sign-in", "Content-Type", "application/json");
        assertThat(emptySignIn.statusCode()).isEqualTo(400);
        assertThat(emptySignIn.headers().allValues(TestServerHttp.HEADER)).containsExactly("noindex");
        assertThat(emptySignIn.body()).doesNotContain("test-server-banner");

        // The web chain still answers as before.
        HttpResponse<String> noCsrf = http.send("POST", "/login");
        assertThat(noCsrf.statusCode()).isEqualTo(403);
        assertThat(noCsrf.headers().allValues(TestServerHttp.HEADER)).containsExactly("noindex");
    }
}
