package com.angel.flexbuddy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.charset.StandardCharsets;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.repository.AppUserRepository;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:spanish;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SpanishIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired ObjectMapper json;
    private static final String EMAIL = "spanish@example.test";

    private AppUser account(String language) {
        AppUser account = new AppUser("Prueba", EMAIL, encoder.encode("local-test-password"));
        account.setEmailVerified(true);
        account.setLanguage(language);
        return users.saveAndFlush(account);
    }

    @Test
    void signedOutPagesAndLegalNotesRenderInSpanish() throws Exception {
        for (String path : new String[]{"/login", "/register", "/forgot-password", "/privacy", "/terms"}) {
            String html = mvc.perform(get(path).header("Accept-Language", "es-MX,es;q=0.9"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertThat(html).as(path).contains("lang=\"es\"").doesNotContain("??");
            if (path.equals("/login")) assertThat(html).contains("Iniciar sesión", "Español", "is-current");
            if (path.equals("/privacy") || path.equals("/terms")) assertThat(html).contains("solo en inglés", "lang=\"en\"");
        }
        mvc.perform(get("/login").param("lang", "es")).andExpect(cookie().value("fb_lang", "es"));
        mvc.perform(get("/login").param("lang", "xx")).andExpect(cookie().value("fb_lang", "en"));
    }

    @Test
    void choicePersistsClearsAndRejectsUnsupportedLanguages() throws Exception {
        AppUser account = account(null);
        mvc.perform(put("/account/language").with(user(EMAIL)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"language\":\"es\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.language").value("es"))
                .andExpect(cookie().value("fb_lang", "es"));
        assertThat(account.getLanguage()).isEqualTo("es");
        mvc.perform(put("/account/language").with(user(EMAIL)).with(csrf())
                .cookie(new Cookie("fb_lang", "es")).header("Accept-Language", "en")
                .contentType(MediaType.APPLICATION_JSON).content("{\"language\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.language").isEmpty())
                .andExpect(cookie().maxAge("fb_lang", 0));
        assertThat(account.getLanguage()).isNull();
        mvc.perform(put("/account/language").with(user(EMAIL)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"language\":\"fr\"}"))
                .andExpect(status().isBadRequest());
        assertThat(account.getLanguage()).isNull();
        mvc.perform(put("/account/language").with(user(EMAIL))
                .contentType(MediaType.APPLICATION_JSON).content("{\"language\":\"es\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void savedLanguageFollowsSignInToANewDeviceAndRendersNavigation() throws Exception {
        account("es");
        var response = mvc.perform(post("/login").with(csrf()).header("Accept-Language", "en")
                .param("username", EMAIL).param("password", "local-test-password"))
                .andExpect(status().is3xxRedirection()).andExpect(cookie().value("fb_lang", "es"))
                .andReturn().getResponse();
        String html = mvc.perform(get("/").with(user(EMAIL)).cookie(response.getCookie("fb_lang")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(html).contains("lang=\"es\"", "Inicio", "Informes", "Programación", "Gastos")
                .doesNotContain("??");
    }

    @Test
    void spanishBackupRestoresLanguageAndUpdatesTheBrowserCookie() throws Exception {
        AppUser account = account("es");
        byte[] backup = mvc.perform(get("/account/backup").with(user(EMAIL))).andExpect(status().isOk())
                .andExpect(jsonPath("$.settings.language").value("es")).andReturn().getResponse().getContentAsByteArray();
        account.setLanguage("en");
        users.saveAndFlush(account);
        MockHttpSession session = new MockHttpSession();
        String preview = mvc.perform(multipart("/account/restore/preview")
                .file(new MockMultipartFile("backup", "backup.json", "application/json", backup))
                .session(session).with(user(EMAIL)).with(csrf())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = json.readTree(preview).get("token").asString();
        mvc.perform(post("/account/restore").session(session).with(user(EMAIL)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"mode\":\"MERGE\",\"includeDeleted\":false,\"acknowledgeReplace\":false}"))
                .andExpect(status().isOk()).andExpect(cookie().value("fb_lang", "es"));
        assertThat(account.getLanguage()).isEqualTo("es");
    }
}
