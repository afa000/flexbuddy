package com.angel.flexbuddy;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The health check Render polls must report on the database and the app, never on the mail server. The mail host here
 * is a closed local port, which refuses connections at once, so the test stays fast.
 */
@SpringBootTest(properties = {"spring.mail.host=127.0.0.1", "spring.mail.port=1"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthCheckIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void healthIsUpWhenTheMailServerIsUnreachable() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("\"status\":\"UP\"")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("mail"))));
    }

    @Test
    void healthNeedsNoSignIn() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().is2xxSuccessful())
                .andExpect(redirectedUrl(null));
    }
}
