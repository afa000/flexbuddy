package com.angel.flexbuddy.testserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.angel.flexbuddy.api.ApiAuthController;
import com.angel.flexbuddy.controller.EmailVerificationController;
import com.angel.flexbuddy.controller.TwoFactorController;
import com.angel.flexbuddy.mail.EmailCodeMailer;
import com.angel.flexbuddy.mail.LoggingEmailCodeMailer;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.PasswordResetToken;
import com.angel.flexbuddy.model.TwoFactorMethod;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.PasswordResetTokenRepository;

/** The hosted test server's switch on, with the native API left off: the two switches stay independent. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "flexbuddy.test-server=true",
        "FLEXBUDDY_MAIL_LOG_LINKS=true",
        "spring.datasource.url=jdbc:h2:mem:testserveron;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TestServerOnIntegrationTest {

    private static final String EMAIL = "test-server@example.test";
    private static final Pattern BANNER_ELEMENT = Pattern.compile("<div class=\"test-server-banner\" role=\"note\">");

    @LocalServerPort int port;
    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;
    @Autowired Environment environment;
    @Autowired EmailCodeMailer emailCodeMailer;
    @Autowired AppUserRepository users;
    @Autowired PasswordResetTokenRepository resets;

    @AfterEach
    void wipe() {
        resets.deleteAll();
        users.deleteAll();
    }

    private AppUser account(String name, String email) {
        AppUser account = new AppUser(name, email, "not-a-real-hash");
        account.setEmailVerified(true);
        return users.saveAndFlush(account);
    }

    private static String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private static long banners(String html) {
        return BANNER_ELEMENT.matcher(html).results().count();
    }

    @Test
    void everyPageTemplateRendersTheBannerOnceWithTheFixedProductionLink() throws Exception {
        AppUser driver = account("Test Driver", EMAIL);
        PasswordResetToken reset = new PasswordResetToken();
        reset.setOwner(driver);
        reset.setCreatedAt(Instant.now());
        reset.setExpiresAt(Instant.now().plusSeconds(600));
        reset.setTokenHash(sha256("test-server-reset"));
        resets.saveAndFlush(reset);

        MockHttpSession verifying = new MockHttpSession();
        verifying.setAttribute(EmailVerificationController.PENDING_USER, driver.getId());
        AppUser twoStep = account("Two Step", "two-step@example.test");
        twoStep.setTwoFactorMethod(TwoFactorMethod.EMAIL);
        twoStep = users.saveAndFlush(twoStep);
        MockHttpSession codeSession = new MockHttpSession();
        codeSession.setAttribute(TwoFactorController.PENDING_USER, twoStep.getId());
        codeSession.setAttribute(TwoFactorController.PENDING_SINCE, Instant.now());

        Map<String, MockHttpServletRequestBuilder> pages = new LinkedHashMap<>();
        pages.put("/login", get("/login"));
        pages.put("/register", get("/register"));
        pages.put("/forgot-password", get("/forgot-password"));
        pages.put("/reset-password (valid)", get("/reset-password").param("token", "test-server-reset"));
        pages.put("/reset-password (invalid)", get("/reset-password").param("token", "no-such-link"));
        pages.put("/verify-email", get("/verify-email").session(verifying));
        pages.put("/sign-in/code", get("/sign-in/code").session(codeSession));
        pages.put("/privacy", get("/privacy"));
        pages.put("/terms", get("/terms"));
        pages.put("/delete-account", get("/delete-account"));
        pages.put("/", get("/").with(user(EMAIL)));
        pages.put("/account", get("/account").with(user(EMAIL)));
        pages.put("/account/two-factor", get("/account/two-factor").with(user(EMAIL)));
        pages.put("/tax/year-summary", get("/tax/year-summary").param("year", "2026").with(user(EMAIL)));

        TreeSet<String> rendered = new TreeSet<>();
        for (Map.Entry<String, MockHttpServletRequestBuilder> page : pages.entrySet()) {
            MvcResult result = mvc.perform(page.getValue()).andExpect(status().isOk())
                    .andExpect(header().string(TestServerHttp.HEADER, "noindex")).andReturn();
            String html = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertThat(banners(html)).as(page.getKey()).isEqualTo(1);
            assertThat(html).as(page.getKey()).contains("<strong>Test server:</strong>", TestServerHttp.BANNER_EN,
                    "for your real blocks.", TestServerHttp.PRODUCTION_LINK);
            // Right after <body>, ahead of the page's own header, navigation and forms.
            assertThat(html.indexOf("test-server-banner")).as(page.getKey())
                    .isGreaterThan(html.indexOf("<body")).isLessThan(html.indexOf("<main"));
            rendered.add(result.getModelAndView().getViewName());
        }
        // The error page and the offline page are rendered over real HTTP in the tests below.
        rendered.add("error");
        rendered.add("offline");
        assertThat(rendered).as("every full-page template is checked")
                .containsExactlyElementsOf(TestServerHttp.pageTemplates());
    }

    @Test
    void everyPageTemplateIncludesTheBannerFragmentRightAfterBody() throws Exception {
        Pattern bodyThenBanner = Pattern.compile(
                "<body[^>]*>\\r?\\n<div th:replace=\"~\\{fragments/test-server :: banner}\"></div>\\r?\\n");
        for (String name : TestServerHttp.pageTemplates()) {
            String template = Files.readString(Path.of("src/main/resources/templates", name + ".html"));
            assertThat(bodyThenBanner.matcher(template).find()).as(name).isTrue();
        }
    }

    @Test
    void bannerIsSpanishForSpanishRequests() throws Exception {
        String html = mvc.perform(get("/login").header("Accept-Language", "es-MX,es;q=0.9"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("<strong>Servidor de pruebas:</strong>", TestServerHttp.BANNER_ES,
                "para tus bloques reales.", TestServerHttp.PRODUCTION_LINK).doesNotContain(TestServerHttp.BANNER_EN);
    }

    @Test
    void bannerNeverReachesDownloadsOrJson() throws Exception {
        account("Test Driver", EMAIL);
        String csv = mvc.perform(get("/shifts/export.csv").with(user(EMAIL))).andExpect(status().isOk())
                .andExpect(header().string(TestServerHttp.HEADER, "noindex"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(csv).doesNotContain("test-server-banner", TestServerHttp.BANNER_EN);
        String json = mvc.perform(get("/account/settings").with(user(EMAIL))).andExpect(status().isOk())
                .andExpect(header().string(TestServerHttp.HEADER, "noindex"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(json).doesNotContain("test-server-banner", "testServer", TestServerHttp.BANNER_EN);
    }

    @Test
    void noindexCoversErrorsStaticFilesRedirectsHeadAndSecurityRejections() throws Exception {
        TestServerHttp http = new TestServerHttp(port);

        for (String path : TestServerHttp.PLAIN_GETS) {
            HttpResponse<String> response = http.get(path);
            assertThat(response.statusCode()).as(path).isEqualTo(200);
            assertThat(response.headers().allValues(TestServerHttp.HEADER)).as(path).containsExactly("noindex");
        }

        HttpResponse<String> errorPage = http.get("/css/no-such-file.css", "Accept", "text/html");
        assertThat(errorPage.statusCode()).isEqualTo(404);
        assertThat(errorPage.headers().allValues(TestServerHttp.HEADER)).containsExactly("noindex");
        assertThat(banners(errorPage.body())).isEqualTo(1);
        assertThat(errorPage.body()).contains(TestServerHttp.BANNER_EN, "error-card");

        HttpResponse<String> jsonError = http.get("/css/no-such-file.css", "Accept", "application/json");
        assertThat(jsonError.statusCode()).isEqualTo(404);
        assertThat(jsonError.headers().allValues(TestServerHttp.HEADER)).containsExactly("noindex");
        assertThat(jsonError.body()).doesNotContain("test-server-banner");

        String lastModified = http.get("/css/styles.css").headers().firstValue("Last-Modified").orElseThrow();
        HttpResponse<String> notModified = http.get("/css/styles.css", "If-Modified-Since", lastModified);
        assertThat(notModified.statusCode()).isEqualTo(304);
        assertThat(notModified.headers().allValues(TestServerHttp.HEADER)).containsExactly("noindex");

        HttpResponse<String> head = http.send("HEAD", "/login");
        assertThat(head.statusCode()).isEqualTo(200);
        assertThat(head.headers().allValues(TestServerHttp.HEADER)).containsExactly("noindex");
        assertThat(head.body()).isEmpty();
        // Spring Security's own headers are still written.
        assertThat(head.headers().firstValue("X-Content-Type-Options")).hasValue("nosniff");

        HttpResponse<String> signedOut = http.get("/account");
        assertThat(signedOut.statusCode()).isEqualTo(302);
        assertThat(signedOut.headers().firstValue("Location").orElseThrow()).endsWith("/login");
        assertThat(signedOut.headers().allValues(TestServerHttp.HEADER)).containsExactly("noindex");

        HttpResponse<String> noCsrf = http.send("POST", "/login", "Accept", "text/html");
        assertThat(noCsrf.statusCode()).isEqualTo(403);
        assertThat(noCsrf.headers().allValues(TestServerHttp.HEADER)).containsExactly("noindex");

        // The API switch is off here, whatever the test-server switch says.
        for (String method : new String[]{"GET", "POST"}) {
            HttpResponse<String> api = http.send(method, "/api/v1/account");
            assertThat(api.statusCode()).as(method).isEqualTo(404);
            assertThat(api.headers().allValues(TestServerHttp.HEADER)).as(method).containsExactly("noindex");
        }
    }

    @Test
    void offlinePageKeepsItsAddressAndCarriesTheBannerInTheRequestLanguage() throws Exception {
        TestServerHttp http = new TestServerHttp(port);
        HttpResponse<String> english = http.get("/offline.html");
        assertThat(english.statusCode()).isEqualTo(200);
        assertThat(english.headers().firstValue("Content-Type").orElseThrow()).startsWith("text/html");
        assertThat(english.headers().firstValue("Set-Cookie")).isEmpty();
        assertThat(banners(english.body())).isEqualTo(1);
        assertThat(english.body()).contains(TestServerHttp.BANNER_EN, TestServerHttp.PRODUCTION_LINK,
                "You&#39;re offline", "window.location.reload()", "lang=\"en\"");

        HttpResponse<String> spanish = http.get("/offline.html", "Accept-Language", "es-MX,es;q=0.9");
        assertThat(spanish.body()).contains(TestServerHttp.BANNER_ES, "Estás sin conexión", "lang=\"es\"")
                .doesNotContain(TestServerHttp.BANNER_EN);

        HttpResponse<String> head = http.send("HEAD", "/offline.html");
        assertThat(head.statusCode()).isEqualTo(200);
        assertThat(head.headers().allValues(TestServerHttp.HEADER)).containsExactly("noindex");
    }

    @Test
    void apiStaysOffAndMailCodesAreLoggedOnlyThroughTheExplicitOverride() {
        assertThat(context.getBeansOfType(ApiAuthController.class)).isEmpty();
        assertThat(context.containsBean("apiSecurityFilterChain")).isFalse();
        assertThat(environment.getProperty("flexbuddy.api.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("flexbuddy.mail.log-links", Boolean.class)).isTrue();
        assertThat(emailCodeMailer).isInstanceOf(LoggingEmailCodeMailer.class);
    }
}
