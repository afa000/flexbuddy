package com.angel.flexbuddy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.repository.AppTokenRepository;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.service.ScreenshotTextExtractor;

import tools.jackson.databind.ObjectMapper;

/**
 * NA-07: an upload over the multipart limits fails inside the real servlet container, before any controller, so this
 * runs against a real port. The upload is made-up bytes only.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"flexbuddy.api.enabled=true", "flexbuddy.security.remember-me-key=api-test-key"})
@ActiveProfiles("test")
class ApiImportUploadLimitTest {
    private static final String EMAIL = "upload-limit@example.test";
    private static final String PASSWORD = "upload-limit-password";

    @LocalServerPort int port;
    @MockitoBean ScreenshotTextExtractor extractor;
    @Autowired AppUserRepository users;
    @Autowired AppTokenRepository tokens;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;

    private final HttpClient http = HttpClient.newHttpClient();

    @AfterEach
    void wipe() {
        tokens.deleteAll();
        users.deleteAll();
    }

    @Test
    void anOversizeScreenshotIsAnsweredAsJson413WithoutReadingIt() throws Exception {
        users.save(new AppUser("Upload Limit", EMAIL, encoder.encode(PASSWORD)));
        String token = signIn();
        // Over the 5 MB file limit but under the 6 MB request limit, so the container reads it all and answers.
        HttpResponse<String> response = upload(token, new byte[5 * 1024 * 1024 + 300 * 1024]);
        assertThat(response.statusCode()).isEqualTo(413);
        assertThat(json.readTree(response.body()).get("error").asText()).isEqualTo("too_large");
        assertThat(response.headers().firstValue("Content-Type").orElse("")).startsWith("application/json");
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        verify(extractor, never()).extract(any());
        // The account isn't left locked: a normal-size (unreadable) upload reaches the reader's checks.
        HttpResponse<String> small = upload(token, "made-up not an image".getBytes(StandardCharsets.UTF_8));
        assertThat(small.statusCode()).isEqualTo(400);
    }

    @Test
    void theWebsitesOwnUploadPathIsLeftAsItWas() throws Exception {
        HttpResponse<String> response = upload("/shifts/import-preview", null, new byte[5 * 1024 * 1024 + 300 * 1024]);
        // The website keeps its own answer (its error handling, not the API JSON).
        assertThat(response.body()).doesNotContain("too_large");
        verify(extractor, never()).extract(any());
    }

    private HttpResponse<String> upload(String token, byte[] content) throws Exception {
        return upload("/api/v1/shifts/import-preview", token, content);
    }

    private HttpResponse<String> upload(String path, String token, byte[] content) throws Exception {
        String boundary = "madeUpBoundary" + UUID.randomUUID().toString().replace("-", "");
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"screenshot\"; filename=\"made-up.png\""
                + "\r\nContent-Type: image/png\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(content);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        HttpRequest request = builder
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String signIn() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/sign-in"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(
                        Map.of("email", EMAIL, "password", PASSWORD)))).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body()).get("token").asText();
    }
}
