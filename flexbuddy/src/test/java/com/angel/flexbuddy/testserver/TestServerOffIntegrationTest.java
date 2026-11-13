package com.angel.flexbuddy.testserver;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import com.angel.flexbuddy.api.ApiAuthController;

/** Production's configuration: the switch is unset, so nothing about the app changes. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.datasource.url=jdbc:h2:mem:testserveroff;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@ActiveProfiles("test")
class TestServerOffIntegrationTest {

    @LocalServerPort int port;
    @Autowired ApplicationContext context;
    @Autowired Environment environment;

    @Test
    void unsetSwitchAddsNoBeansNoBannerAndNoHeader() throws Exception {
        assertThat(environment.getProperty(TestServerConfig.PROPERTY, Boolean.class)).isFalse();
        assertThat(environment.getProperty("flexbuddy.mail.log-links", Boolean.class)).isFalse();
        assertThat(context.getBeansOfType(ApiAuthController.class)).isEmpty();
        TestServerOffChecks.assertUnchanged(context, new TestServerHttp(port));
        // The API stays off on its own default.
        HttpResponse<String> api = new TestServerHttp(port).get("/api/v1/account");
        assertThat(api.statusCode()).isEqualTo(404);
        assertThat(api.headers().allValues(TestServerHttp.HEADER)).isEmpty();
    }
}

/** What "off" means, shared by the unset and explicit-false contexts. */
final class TestServerOffChecks {

    private TestServerOffChecks() {
    }

    static void assertUnchanged(ApplicationContext context, TestServerHttp http) throws Exception {
        assertThat(context.getBeansOfType(TestServerConfig.class)).isEmpty();
        assertThat(context.getBeansOfType(TestServerAdvice.class)).isEmpty();
        assertThat(context.getBeansOfType(TestServerOfflineController.class)).isEmpty();
        assertThat(context.containsBean("testServerNoIndexFilter")).isFalse();

        for (String path : TestServerHttp.PLAIN_GETS) {
            HttpResponse<String> response = http.get(path);
            assertThat(response.statusCode()).as(path).isEqualTo(200);
            assertThat(response.headers().allValues(TestServerHttp.HEADER)).as(path).isEmpty();
            // The stylesheet always holds the banner's rule; no page or other file may show the banner itself.
            if (!path.endsWith(".css")) {
                assertThat(response.body()).as(path).doesNotContain("test-server-banner", TestServerHttp.BANNER_EN);
            }
        }
        HttpResponse<String> errorPage = http.get("/css/no-such-file.css", "Accept", "text/html");
        assertThat(errorPage.statusCode()).isEqualTo(404);
        assertThat(errorPage.headers().allValues(TestServerHttp.HEADER)).isEmpty();
        assertThat(errorPage.body()).contains("error-card").doesNotContain("test-server-banner");

        HttpResponse<String> head = http.send("HEAD", "/login");
        assertThat(head.statusCode()).isEqualTo(200);
        assertThat(head.headers().allValues(TestServerHttp.HEADER)).isEmpty();
        HttpResponse<String> signedOut = http.get("/account");
        assertThat(signedOut.statusCode()).isEqualTo(302);
        assertThat(signedOut.headers().allValues(TestServerHttp.HEADER)).isEmpty();
        HttpResponse<String> noCsrf = http.send("POST", "/login");
        assertThat(noCsrf.statusCode()).isEqualTo(403);
        assertThat(noCsrf.headers().allValues(TestServerHttp.HEADER)).isEmpty();

        // The static offline page, byte for byte, is what the service worker keeps.
        HttpResponse<String> offline = http.get("/offline.html");
        assertThat(offline.body()).isEqualTo(Files.readString(Path.of("src/main/resources/static/offline.html")));
        assertThat(offline.headers().firstValue("Last-Modified")).isPresent();
    }
}
