package com.angel.flexbuddy.service;

import java.math.BigDecimal;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.angel.flexbuddy.dto.AccountSettingsRequest;
import com.angel.flexbuddy.dto.AccountSettingsResponse;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.VehicleCostMethod;
import com.angel.flexbuddy.repository.AppUserRepository;

@Service
public class AccountSettingsService {
    private final AppUserRepository userRepository;
    private final BigDecimal defaultMileageRate;
    private final int mileageRateYear;

    public AccountSettingsService(AppUserRepository userRepository,
            @Value("${flexbuddy.mileage.default-rate:${flexbuddy.mileage-rate:0.70}}") BigDecimal defaultMileageRate,
            @Value("${flexbuddy.mileage.default-rate-year:${flexbuddy.mileage-rate-year:2025}}") int mileageRateYear) {
        this.userRepository = userRepository;
        this.defaultMileageRate = defaultMileageRate;
        this.mileageRateYear = mileageRateYear;
    }

    @Transactional(readOnly = true)
    public AccountSettingsResponse get(String email) {
        return response(user(email));
    }

    @Transactional
    public AccountSettingsResponse update(String email, AccountSettingsRequest request) {
        AppUser user = user(email);
        user.setVehicleCostMethod(request.vehicleCostMethod());
        user.setMileageRate(request.mileageRate());
        return response(userRepository.save(user));
    }

    public BigDecimal effectiveMileageRate(AppUser user) {
        return user.getMileageRate() == null ? defaultMileageRate : user.getMileageRate();
    }

    private AccountSettingsResponse response(AppUser user) {
        VehicleCostMethod method = user.getVehicleCostMethod() == null
                ? VehicleCostMethod.STANDARD_MILEAGE : user.getVehicleCostMethod();
        return new AccountSettingsResponse(method, effectiveMileageRate(user), defaultMileageRate, mileageRateYear);
    }

    private AppUser user(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalStateException("Signed-in account could not be found."));
    }
}
