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
}
