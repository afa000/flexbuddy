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

/** An explicit false behaves like unset, and turning the API on does not turn the test server on. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "flexbuddy.test-server=false",
        "flexbuddy.api.enabled=true",
        "spring.datasource.url=jdbc:h2:mem:testserverfalse;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test")
class TestServerExplicitlyOffIntegrationTest {

    @LocalServerPort int port;
    @Autowired ApplicationContext context;

    @Test
    void explicitFalseIsUnchangedEvenWithTheApiOn() throws Exception {
        assertThat(context.getBeansOfType(ApiAuthController.class)).hasSize(1);
        TestServerOffChecks.assertUnchanged(context, new TestServerHttp(port));
        HttpResponse<String> api = new TestServerHttp(port).get("/api/v1/account");
        assertThat(api.statusCode()).isEqualTo(401);
        assertThat(api.body()).isEqualTo("{\"error\":\"unauthorized\"}");
        assertThat(api.headers().allValues(TestServerHttp.HEADER)).isEmpty();
    }
}
