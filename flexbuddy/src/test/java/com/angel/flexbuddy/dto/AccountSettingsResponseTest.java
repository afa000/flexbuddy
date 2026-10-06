package com.angel.flexbuddy.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.angel.flexbuddy.model.GoalBasis;
import com.angel.flexbuddy.model.VehicleCostMethod;

class AccountSettingsResponseTest {

    @Test
    void theSeventeenArgumentConstructorShowsTheMissingMilesPromptsAsOn() {
        var settings = new AccountSettingsResponse(VehicleCostMethod.STANDARD_MILEAGE, new BigDecimal("0.700"),
                new BigDecimal("0.700"), 2025, "America/Chicago", null, false, false, 45, null, null, GoalBasis.GROSS,
                List.of(), 1, null, null, false);

        assertThat(settings.askMissingMiles()).isTrue();
    }
}
