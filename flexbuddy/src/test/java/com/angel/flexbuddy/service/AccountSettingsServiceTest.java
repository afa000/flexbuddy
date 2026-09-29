package com.angel.flexbuddy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.angel.flexbuddy.dto.AccountSettingsRequest;
import com.angel.flexbuddy.dto.ReminderSettingsRequest;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.VehicleCostMethod;
import com.angel.flexbuddy.repository.AppUserRepository;

@ExtendWith(MockitoExtension.class)
class AccountSettingsServiceTest {
    private static final String EMAIL = "angel@example.com";

    @Mock AppUserRepository userRepository;

    private AccountSettingsService service;
    private AppUser user;

    @BeforeEach
    void setUp() {
        service = new AccountSettingsService(userRepository, new BigDecimal("0.700"), 2025);
        user = new AppUser("Angel", EMAIL, "hash");
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(user));
    }

    @Test
    void getUsesTheConfiguredDefaultWhenTheUserHasNoCustomRate() {
        var settings = service.get(EMAIL);

        assertThat(settings.vehicleCostMethod()).isEqualTo(VehicleCostMethod.STANDARD_MILEAGE);
        assertThat(settings.mileageRate()).isEqualByComparingTo("0.700");
        assertThat(settings.mileageRateYear()).isEqualTo(2025);
    }

    @Test
    void updatePersistsTheSelectedMethodAndCustomRate() {
        when(userRepository.save(user)).thenReturn(user);

        var settings = service.update(EMAIL,
                new AccountSettingsRequest(VehicleCostMethod.ACTUAL_EXPENSES, new BigDecimal("0.655")));

        assertThat(settings.vehicleCostMethod()).isEqualTo(VehicleCostMethod.ACTUAL_EXPENSES);
        assertThat(settings.mileageRate()).isEqualByComparingTo("0.655");
        verify(userRepository).save(user);
    }

    @Test
    void aNullCustomRateResetsToTheConfiguredDefault() {
        user.setMileageRate(new BigDecimal("0.500"));
        when(userRepository.save(user)).thenReturn(user);

        var settings = service.update(EMAIL,
                new AccountSettingsRequest(VehicleCostMethod.STANDARD_MILEAGE, null));

        assertThat(user.getMileageRate()).isNull();
        assertThat(settings.mileageRate()).isEqualByComparingTo("0.700");
    }

    @Test
    void updateRemindersStoresTheZoneLeadTimeAndConfirmationNudge() {
        when(userRepository.save(user)).thenReturn(user);

        var settings = service.updateReminders(EMAIL, new ReminderSettingsRequest("America/Chicago", 120, true, true, 60));

        assertThat(settings.timeZone()).isEqualTo("America/Chicago");
        assertThat(settings.remindBeforeMinutes()).isEqualTo(120);
        assertThat(settings.remindConfirm()).isTrue();
        assertThat(settings.remindMiles()).isTrue();
        assertThat(settings.forfeitCutoffMinutes()).isEqualTo(60);
    }

    @Test
    void updateTaxTurnsDueDateRemindersOnAndKeepsThemWhenOmitted() {
        when(userRepository.save(user)).thenReturn(user);

        var on = service.updateTax(EMAIL, new com.angel.flexbuddy.dto.TaxSettingsRequest(new BigDecimal("25.00"), Boolean.TRUE));
        assertThat(on.remindTax()).isTrue();
        assertThat(on.taxSetAsidePercent()).isEqualByComparingTo("25.00");

        var kept = service.updateTax(EMAIL, new com.angel.flexbuddy.dto.TaxSettingsRequest(new BigDecimal("30.00")));
        assertThat(kept.remindTax()).isTrue();
        assertThat(kept.taxSetAsidePercent()).isEqualByComparingTo("30.00");

        var off = service.updateTax(EMAIL, new com.angel.flexbuddy.dto.TaxSettingsRequest(null, Boolean.FALSE));
        assertThat(off.remindTax()).isFalse();
        assertThat(off.taxSetAsidePercent()).isNull();
    }

    @Test
    void regeneratingTheCalendarTokenReplacesTheFeedLink() {
        when(userRepository.save(user)).thenReturn(user);

        String first = service.regenerateCalendarToken(EMAIL).calendarFeedPath();
        String second = service.regenerateCalendarToken(EMAIL).calendarFeedPath();

        assertThat(first).matches("/calendar/[A-Za-z0-9_-]{43}\\.ics");
        assertThat(second).matches("/calendar/[A-Za-z0-9_-]{43}\\.ics").isNotEqualTo(first);
        assertThat(second).isEqualTo("/calendar/" + user.getCalendarToken() + ".ics");
    }
}
