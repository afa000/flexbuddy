package com.angel.flexbuddy.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.service.AccountSettingsService;

class ApiCalculationSettingsTest {
    @Test
    void legacyNullSettingsAreMappedAfterTheExistingServicesDefaults() {
        // Test legacy in-memory defaults without violating today's database NOT NULL constraints.
        AppUser user = new AppUser("Synthetic driver", "settings@example.test", "unused-hash");
        user.setVehicleCostMethod(null);
        user.setGoalBasis(null);
        user.setTimeZone(null);
        user.setPayoutDays(null);
        AppUserRepository users = mock(AppUserRepository.class);
        when(users.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        var service = new AccountSettingsService(users, Clock.fixed(Instant.parse("2026-07-01T12:00:00Z"), ZoneOffset.UTC));

        var result = ApiCalculationSettings.of(service.get(user.getEmail()));

        assertThat(result.timeZone()).isEqualTo(AppUser.DEFAULT_TIME_ZONE);
        assertThat(result.vehicleCostMethod()).isEqualTo("standard_mileage");
        assertThat(result.goalBasis()).isEqualTo("gross");
        assertThat(result.customMileageRate()).isNull();
        assertThat(result.payoutDays()).isEqualTo(List.of("tuesday", "friday"));
    }
}
