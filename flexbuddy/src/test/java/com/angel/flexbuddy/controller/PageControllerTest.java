package com.angel.flexbuddy.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.angel.flexbuddy.config.SecurityConfig;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.ShiftRepository;

@WebMvcTest(PageController.class)
@Import(SecurityConfig.class)
class PageControllerTest {

    private static final MockMultipartFile SHARED = new MockMultipartFile(
            "screenshot", "earnings.png", "image/png", new byte[] {1, 2, 3});

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AppUserRepository userRepository;

    @MockitoBean
    private ShiftRepository shiftRepository;

    @MockitoBean
    private Clock clock;

    @Test
    void shareWithoutServiceWorkerSendsDriverToImportWithoutCsrfToken() throws Exception {
        mockMvc.perform(multipart("/share-import").file(SHARED).with(user("angel@example.com")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?screen=import&shared=unavailable"));
    }

    @Test
    void shiftsPageRendersTheQuickActionMenu() throws Exception {
        String page = mockMvc.perform(get("/").with(user("angel@example.com")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(page).contains("id=\"quickActionButton\"", "aria-haspopup=\"menu\"", "aria-expanded=\"false\"",
                "role=\"menu\"", "/js/quick-actions.js?v=");
        for (String row : new String[] {"qaImport", "qaAddShift", "qaAddExpense", "qaStartBlock"}) {
            assertThat(page).containsPattern("<button[^>]*id=\"" + row + "\"[^>]*role=\"menuitem\"[^>]*data-online-only");
        }
        // The stylesheet lifts the toast above the button with a sibling selector, so the button must come first.
        assertThat(page.indexOf("id=\"quickActionButton\"")).isPositive()
                .isLessThan(page.indexOf("id=\"successToast\""));
    }

    @Test
    void shareRequiresSignIn() throws Exception {
        mockMvc.perform(multipart("/share-import").file(SHARED))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
