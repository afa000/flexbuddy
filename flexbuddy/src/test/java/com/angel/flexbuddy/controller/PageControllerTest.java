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
import com.angel.flexbuddy.model.AppUser;
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
    private com.angel.flexbuddy.repository.RecoveryCodeRepository recoveryCodeRepository;

    @MockitoBean
    private Clock clock;

    @Test
    void shareWithoutServiceWorkerSendsDriverToImportWithoutCsrfToken() throws Exception {
        mockMvc.perform(multipart("/share-import").file(SHARED).with(user("angel@example.com")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?screen=import&shared=unavailable"));
    }

    @Test
    void theHomePageCarriesTheScriptTextBeforeItsFirstScript() throws Exception {
        String page = mockMvc.perform(get("/").with(user("angel@example.com")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(page).contains("<script id=\"flexbuddyMessages\" type=\"application/json\">{\"js.");
        assertThat(page).contains("No blocks yet. Add one from the + button.");
        assertThat(page.indexOf("id=\"flexbuddyMessages\"")).isPositive().isLessThan(page.indexOf("/js/errors.js?v="));
        assertThat(page.indexOf("/js/i18n.js?v=")).isGreaterThan(page.indexOf("/js/errors.js?v="))
                .isLessThan(page.indexOf("/js/toast.js?v="));
    }

    @Test
    void theImportReviewMakesTheDriverChooseWhetherTheBlockWasWorked() throws Exception {
        String page = mockMvc.perform(get("/").with(user("angel@example.com")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Required, and the empty choice is the selected first option, so neither a new screenshot nor a form reset
        // lands on Completed.
        assertThat(page).containsPattern("<select id=\"importStatus\"[^>]*\\brequired\\b[^>]*>\\s*"
                + "<option value=\"\" selected>Choose one</option>\\s*"
                + "<option value=\"COMPLETED\">Completed · I worked this block</option>\\s*"
                + "<option value=\"SCHEDULED\">Scheduled · accepted, not worked yet</option>");
        assertThat(page).contains("id=\"importStatusHint\"", "/js/import-status.js?v=");
        assertThat(page.indexOf("/js/import-status.js?v=")).isLessThan(page.indexOf("/js/app.js?v="));
    }

    @Test
    void theEditDialogOffersToMoveAWronglyCompletedBlockBackBehindAConfirmation() throws Exception {
        String page = mockMvc.perform(get("/").with(user("angel@example.com")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Hidden until the script sees a saved completed block; the confirmation is hidden until asked for.
        assertThat(page).containsPattern("<button class=\"text-button is-hidden\" id=\"notWorkedButton\" type=\"button\">"
                + "Not worked yet\\?</button>");
        assertThat(page).containsPattern("<div class=\"not-worked-confirm field-wide is-hidden\" id=\"notWorkedConfirm\"");
        assertThat(page).contains("Move this block back to Scheduled?", "id=\"notWorkedCleared\"",
                "id=\"cancelNotWorkedButton\"", ">Move to Scheduled</button>", "/js/not-worked.js?v=");
        assertThat(page.indexOf("/js/not-worked.js?v=")).isLessThan(page.indexOf("/js/app.js?v="));
    }

    @Test
    void theExpenseFormSaysWhatTheChosenCategoryDoesToNet() throws Exception {
        String page = mockMvc.perform(get("/").with(user("angel@example.com")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // The hint sits inside the Category label, so it is part of the select's name without aria-describedby.
        assertThat(page).containsPattern("<select id=\"expenseCategory\" required>.*?</select>"
                + "<small class=\"field-hint\" id=\"expenseCategoryHint\" aria-live=\"polite\"></small></label>");
        assertThat(page.indexOf("/js/expense-effect.js?v=")).isPositive().isLessThan(page.indexOf("/js/app.js?v="));
    }

    @Test
    void homeAndTheEditDialogOfferTheBlockChecklist() throws Exception {
        String page = mockMvc.perform(get("/").with(user("angel@example.com")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(page).contains("id=\"lastBlockCard\" hidden",
                "id=\"lastBlockButton\" type=\"button\">Review this block</button>",
                "id=\"reviewBlockButton\" type=\"button\">Review this block</button>",
                "id=\"blockReviewModal\"", "<p class=\"step-label\">REVIEW THIS BLOCK</p>", "/js/block-review.js");
    }

    @Test
    void theImportCardHasTheAndroidSharingTipHiddenUntilTheScriptShowsIt() throws Exception {
        String page = mockMvc.perform(get("/").with(user("angel@example.com")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(page).contains("<small class=\"field-hint is-hidden\" id=\"shareTip\">On Android, you can also share screenshots to FlexBuddy from Photos.</small>",
                "/js/share-inbox.js");
    }

    @Test
    void theImportCardTakesSeveralScreenshotsAndHasTheBatchList() throws Exception {
        String page = mockMvc.perform(get("/").with(user("angel@example.com")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(page).contains("<input id=\"screenshotInput\" type=\"file\" accept=\"image/png,image/jpeg\" multiple hidden>",
                "You can choose up to 10 at once.",
                "id=\"importQueue\" aria-label=\"Screenshots in this import\"",
                ">Stop importing</button>", ">Skip this screenshot</button>", "/js/import-queue.js");
    }

    @Test
    void theImportCardSpeaksToTouchAndPointerAndSaysWhichScreensWork() throws Exception {
        String page = mockMvc.perform(get("/").with(user("angel@example.com")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(page).contains("<strong class=\"pointer-copy\">Drop your screenshot here</strong>",
                "<strong class=\"touch-copy\">Choose a screenshot</strong>",
                "<span class=\"touch-copy\">Tap to pick one from your photos</span>",
                "Works with a Schedule Details screenshot of a block you accepted",
                "An empty Offers screen has no block to import.");
    }

    @Test
    void shiftsPageRendersTheQuickActionMenu() throws Exception {
        String page = mockMvc.perform(get("/").with(user("angel@example.com")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(page).contains("id=\"quickActionButton\"", "aria-haspopup=\"menu\"", "aria-expanded=\"false\"",
                "role=\"menu\"", "/js/quick-actions.js?v=");
        // Importing and starting a block need a connection; adding a shift or an expense is queued offline.
        for (String row : new String[] {"qaImport", "qaStartBlock"}) {
            assertThat(page).containsPattern("<button[^>]*id=\"" + row + "\"[^>]*role=\"menuitem\"[^>]*data-online-only");
        }
        for (String row : new String[] {"qaAddShift", "qaAddExpense"}) {
            assertThat(page).containsPattern("<button[^>]*id=\"" + row + "\"[^>]*role=\"menuitem\"[^>]*>");
            assertThat(page).doesNotContainPattern("<button[^>]*id=\"" + row + "\"[^>]*data-online-only");
        }
        // The stylesheet lifts the toast above the button with a sibling selector, so the button must come first.
        assertThat(page.indexOf("id=\"quickActionButton\"")).isPositive()
                .isLessThan(page.indexOf("id=\"successToast\""));
    }

    @Test
    void shiftsPageHasTheStandingCardAndTileLink() throws Exception {
        String page = mockMvc.perform(get("/").with(user("angel@example.com")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(page).contains("id=\"standingPanel\"", "id=\"standingLink\"", "id=\"standingPill\"",
                "/js/standing.js?v=");
        for (String level : new String[] {"FANTASTIC", "GREAT", "FAIR", "AT_RISK"}) {
            assertThat(page).contains("data-level=\"" + level + "\"");
        }
    }

    @Test
    void bottomBarHasReportsAndNoImport() throws Exception {
        String page = mockMvc.perform(get("/").with(user("angel@example.com")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(page).contains("id=\"reportsNavButton\"").doesNotContain("id=\"importNavButton\"");
        // Import stays one tap away in the + button.
        assertThat(page).contains("id=\"qaImport\"");
    }

    @Test
    void reportsScreenHoldsTheRangeTabsAndHistory() throws Exception {
        String page = mockMvc.perform(get("/").with(user("angel@example.com")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(page).contains("id=\"reportsScreen\"", "id=\"rangeChip\"", "id=\"rangeSheet\"", "role=\"tablist\"",
                "id=\"historyList\"", "id=\"earningsChart\"", "/js/reports.js?v=");
        // The history and the charts sit inside the Reports screen, which is what moved them off the dashboard.
        assertThat(page.indexOf("id=\"historyList\"")).isGreaterThan(page.indexOf("id=\"reportsScreen\""));
        assertThat(page.indexOf("id=\"earningsChart\"")).isGreaterThan(page.indexOf("id=\"reportsScreen\""));
    }

    @Test
    void homeReplacesTheTitleBannerAndTiles() throws Exception {
        String page = mockMvc.perform(get("/").with(user("angel@example.com")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(page).contains("id=\"homeScreen\"", "id=\"goalCard\"", "id=\"homeNextBlock\"", "id=\"homeAttention\"",
                "id=\"recentList\"", "id=\"evaluatePanel\"", "/js/home.js?v=");
        assertThat(page).doesNotContain("Track every block, hour, and dollar", "id=\"plannedWeek\"", "id=\"nextShiftLink\"",
                "class=\"backup-nudge\"");
        // The dashboard's own title banner is gone; the account page keeps the .hero style for itself.
        assertThat(page).doesNotContain("class=\"hero\"");
        // The week, payout and recent blocks come before Reports, whose tiles follow a range.
        int reports = page.indexOf("id=\"reportsScreen\"");
        for (String id : new String[] {"goalCard", "payoutCard", "rollingSevenDayTime", "taxWeekAmount", "recentList"}) {
            assertThat(page.indexOf("id=\"" + id + "\"")).as(id).isPositive().isLessThan(reports);
        }
        assertThat(page.indexOf("id=\"totalEarnings\"")).isGreaterThan(reports);
    }

    @Test
    void theGreetingUsesTheDisplayName() throws Exception {
        AppUser angel = new AppUser("Angel", "angel@example.com", "hash");
        angel.setId(42L);
        org.mockito.Mockito.when(userRepository.findByEmailIgnoreCase("angel@example.com")).thenReturn(java.util.Optional.of(angel));

        String page = mockMvc.perform(get("/").with(user("angel@example.com")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(page).contains("Hi, Angel");
    }

    @Test
    void backupDueIsAnAttributeOnTheAttentionCard() throws Exception {
        AppUser angel = new AppUser("Angel", "angel@example.com", "hash");
        angel.setId(42L);
        org.mockito.Mockito.when(userRepository.findByEmailIgnoreCase("angel@example.com")).thenReturn(java.util.Optional.of(angel));
        org.mockito.Mockito.when(shiftRepository.countByOwnerEmailIgnoreCase("angel@example.com")).thenReturn(11L);

        String page = mockMvc.perform(get("/").with(user("angel@example.com")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(page).containsPattern("<article[^>]*id=\"homeAttention\"[^>]*data-backup-due=\"true\"");
        // The old banner is not rendered any more; Home's card lists the row instead.
        assertThat(page).doesNotContain("Download a backup so you can recover it");
    }

    @Test
    void forfeitsMovedToSchedule() throws Exception {
        String page = mockMvc.perform(get("/").with(user("angel@example.com")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        int schedule = page.indexOf("id=\"scheduleScreen\"");
        assertThat(page.indexOf("id=\"forfeitsMonth\"")).isGreaterThan(schedule);
        // The pill comes with it, because standing.js reads it without a null check.
        assertThat(page.indexOf("id=\"standingPill\"")).isGreaterThan(schedule);
        assertThat(page.indexOf("id=\"standingPill\"")).isLessThan(page.indexOf("id=\"standingPanel\""));
    }

    @Test
    void theFirstNavItemReadsHome() throws Exception {
        String page = mockMvc.perform(get("/").with(user("angel@example.com")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(page).containsPattern("id=\"dashboardNavButton\"[^>]*>\\s*<svg[^>]*>.*?</svg>\\s*<span>Home</span>");
    }

    private String pageFor(AppUser user, long shiftCount) throws Exception {
        org.mockito.Mockito.when(userRepository.findByEmailIgnoreCase("angel@example.com")).thenReturn(java.util.Optional.of(user));
        org.mockito.Mockito.when(shiftRepository.countByOwnerEmailIgnoreCase("angel@example.com")).thenReturn(shiftCount);
        return mockMvc.perform(get("/").with(user("angel@example.com")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @Test
    void aNewDriverSeesTheSetupCardWithNoBlocks() throws Exception {
        String page = pageFor(new AppUser("Angel", "angel@example.com", "hash"), 0);

        assertThat(page).containsPattern("<article[^>]*id=\"setupCard\"[^>]*data-has-blocks=\"false\"");
        assertThat(page).contains("id=\"setupList\"", "id=\"setupDone\"", "id=\"setupHide\"", "/js/setup.js?v=", "/js/feedback.js?v=");
        // The card comes before the week card.
        assertThat(page.indexOf("id=\"setupCard\"")).isLessThan(page.indexOf("id=\"goalCard\""));
    }

    @Test
    void aDriverWhoDismissedTheCardDoesNotSeeIt() throws Exception {
        AppUser angel = new AppUser("Angel", "angel@example.com", "hash");
        angel.setSetupDismissedAt(java.time.Instant.parse("2026-10-01T12:00:00Z"));

        assertThat(pageFor(angel, 0)).doesNotContain("id=\"setupCard\"");
    }

    @Test
    void theSetupCardKnowsWhenThereAreBlocks() throws Exception {
        String page = pageFor(new AppUser("Angel", "angel@example.com", "hash"), 3);

        assertThat(page).containsPattern("<article[^>]*id=\"setupCard\"[^>]*data-has-blocks=\"true\"");
    }

    @Test
    void homeAlwaysOffersFeedbackThatOpensTheEmailApp() throws Exception {
        AppUser angel = new AppUser("Angel", "angel@example.com", "hash");
        angel.setSetupDismissedAt(java.time.Instant.parse("2026-10-01T12:00:00Z"));

        String page = pageFor(angel, 5);

        assertThat(page).containsPattern("<p class=\"home-feedback\"><a[^>]*href=\"mailto:flexbuddysupport@gmail.com\"[^>]*data-feedback");
    }

    @Test
    void homeCarriesTheMissingMilesChoiceAndALinkToTheSetting() throws Exception {
        AppUser angel = new AppUser("Angel", "angel@example.com", "hash");

        String on = pageFor(angel, 3);
        angel.setAskMissingMiles(false);
        String off = pageFor(angel, 3);

        assertThat(on).containsPattern("id=\"missingMiles\"[^>]*data-ask=\"true\"");
        assertThat(off).containsPattern("id=\"missingMiles\"[^>]*data-ask=\"false\"");
        assertThat(on).containsPattern("<a[^>]*href=\"/account#reminders\"[^>]*>Stop asking</a>");
    }

    @Test
    void homeCarriesTheRecoveryCodesLeftOnlyForADriverWithTwoStepSignIn() throws Exception {
        AppUser plain = new AppUser("Angel", "angel@example.com", "hash");
        assertThat(pageFor(plain, 3)).doesNotContain("data-recovery-left");

        AppUser protectedUser = new AppUser("Angel", "angel@example.com", "hash");
        protectedUser.setId(7L);
        protectedUser.setTwoFactorMethod(com.angel.flexbuddy.model.TwoFactorMethod.APP);
        org.mockito.Mockito.when(recoveryCodeRepository.countByOwnerIdAndUsedAtIsNull(7L)).thenReturn(9L);
        assertThat(pageFor(protectedUser, 3)).contains("data-recovery-left=\"9\"");
    }

    @Test
    void importScreenOffersCancelAndChooseAgain() throws Exception {
        String page = pageFor(new AppUser("Angel", "angel@example.com", "hash"), 3);

        assertThat(page).containsPattern("(?s)id=\"processingRow\".*?id=\"cancelImportButton\"");
        assertThat(page).containsPattern("id=\"chooseAgainButton\"[^>]*>Choose a different screenshot<");
        // The remove button used to be disabled for as long as the server took to read the image.
        assertThat(page).doesNotContainPattern("id=\"removeFileButton\"[^>]*disabled");
    }

    @Test
    void headerHasHomeLogoAndAccountButtonButNoSignOut() throws Exception {
        AppUser angel = new AppUser("Angel", "angel@example.com", "hash");
        angel.setId(42L);
        org.mockito.Mockito.when(userRepository.findByEmailIgnoreCase("angel@example.com")).thenReturn(java.util.Optional.of(angel));

        String page = mockMvc.perform(get("/").with(user("angel@example.com")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(page).contains("id=\"homeLink\"", "aria-label=\"FlexBuddy, go to Home\"");
        assertThat(page).containsPattern("class=\"round-header-button account-button\"[^>]*aria-label=\"Account and settings\"");
        assertThat(page).containsPattern("(?s)class=\"round-header-button account-button\".*?<svg.*?</a>");
        // The first letter of the name read as a profile picture, so it is gone.
        assertThat(page).doesNotContainPattern("class=\"round-header-button account-button\"[^>]*>\\s*A\\s*</a>");
        assertThat(page).doesNotContain("id=\"logoutForm\"", "id=\"dashboardButton\"", "<small>Shift tracker</small>", "account-name");
    }

    @Test
    void screenTitlesAreOneLine() throws Exception {
        String page = mockMvc.perform(get("/").with(user("angel@example.com")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(page).doesNotContain("class=\"screen-heading", "YOUR SCHEDULE", "IMPORT A SHIFT", "DRIVER EXPENSES");
        assertThat(page).contains("<h1 id=\"schedule-title\">Schedule</h1>", "<h1 id=\"import-title\">Import a shift</h1>",
                "<h1 id=\"expenses-title\">Expenses</h1>");
        // The button sits on the title's line.
        assertThat(page).containsPattern("(?s)<div class=\"screen-title\">\\s*<h1 id=\"schedule-title\">.*?id=\"addScheduledShiftButton\"");
    }

    @Test
    void onlyMeaningfulLabelsRemain() throws Exception {
        String page = mockMvc.perform(get("/").with(user("angel@example.com")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(page).contains("STEP 1", "STEP 2", "BEFORE YOU ACCEPT", "SHIFT DETAILS", "FINISH BLOCK");
        assertThat(page).doesNotContain("NEXT 14 DAYS", "QUICK ADD", "EXPENSE HISTORY", "NEEDS CONFIRMATION", "step-number");
        assertThat(page).contains("Next 14 days", "Expense history");
        // The standing promise stays, as a hint under the form.
        assertThat(page).contains("FlexBuddy never guesses your standing.");
    }

    @Test
    void expenseCostMethodIsANoteNotATile() throws Exception {
        String page = mockMvc.perform(get("/").with(user("angel@example.com")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(page).containsPattern("(?s)class=\"expense-cost-note\">.*?id=\"expenseCostMethod\".*?href=\"/account#costs\"");
        assertThat(page).doesNotContain("Vehicle cost method");
    }

    @Test
    void shiftsPageCarriesTheAccountIdAndTheOutbox() throws Exception {
        AppUser angel = new AppUser("Angel", "angel@example.com", "hash");
        angel.setId(42L);
        org.mockito.Mockito.when(userRepository.findByEmailIgnoreCase("angel@example.com")).thenReturn(java.util.Optional.of(angel));

        String page = mockMvc.perform(get("/").with(user("angel@example.com")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(page).contains("<meta name=\"flexbuddy-account\" content=\"42\">", "id=\"outboxStrip\"",
                "id=\"conflictModal\"", "/js/outbox.js?v=");
        // The forms that can be saved offline keep their Save button; nothing else about them changes.
        for (String form : new String[] {"editForm", "expenseForm", "finishForm"}) {
            assertThat(page).containsPattern("<form[^>]*id=\"" + form + "\"[^>]*data-queueable");
        }
        assertThat(page).doesNotContainPattern("<button[^>]*id=\"addScheduledShiftButton\"[^>]*data-online-only");
        // Completing from a screenshot is a separate write that is not queued.
        assertThat(page).containsPattern("<button[^>]*id=\"completeScheduledButton\"[^>]*data-online-only");
    }

    @Test
    void theAccountMetaIsEmptyWithoutAUser() throws Exception {
        String page = mockMvc.perform(get("/").with(user("angel@example.com")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // Thymeleaf drops an empty attribute, and either way the script reads no account id.
        assertThat(page).containsPattern("<meta name=\"flexbuddy-account\"( content=\"\")?>");
    }

    @Test
    void shareRequiresSignIn() throws Exception {
        mockMvc.perform(multipart("/share-import").file(SHARED))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }
}
