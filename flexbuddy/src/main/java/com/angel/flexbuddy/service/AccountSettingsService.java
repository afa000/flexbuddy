package com.angel.flexbuddy.service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.angel.flexbuddy.dto.AccountSettingsRequest;
import com.angel.flexbuddy.dto.AccountSettingsResponse;
import com.angel.flexbuddy.dto.ReminderSettingsRequest;
import com.angel.flexbuddy.dto.TimeZoneRequest;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.VehicleCostMethod;
import com.angel.flexbuddy.repository.AppUserRepository;

@Service
public class AccountSettingsService {
    private static final SecureRandom RANDOM = new SecureRandom();

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

    @Transactional
    public AccountSettingsResponse updateReminders(String email, ReminderSettingsRequest request) {
        AppUser user = user(email);
        user.setTimeZone(request.timeZone());
        user.setRemindBeforeMinutes(request.remindBeforeMinutes());
        user.setRemindConfirm(request.remindConfirm());
        return response(userRepository.save(user));
    }

    @Transactional
    public AccountSettingsResponse updateTimeZone(String email, TimeZoneRequest request) {
        AppUser user = user(email);
        user.setTimeZone(request.timeZone());
        return response(userRepository.save(user));
    }

    /** Issues a new feed token, which immediately invalidates any calendar subscribed to the old link. */
    @Transactional
    public AccountSettingsResponse regenerateCalendarToken(String email) {
        AppUser user = user(email);
        user.setCalendarToken(newCalendarToken());
        return response(userRepository.save(user));
    }

    public BigDecimal effectiveMileageRate(AppUser user) {
        return user.getMileageRate() == null ? defaultMileageRate : user.getMileageRate();
    }

    static String newCalendarToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private AccountSettingsResponse response(AppUser user) {
        VehicleCostMethod method = user.getVehicleCostMethod() == null
                ? VehicleCostMethod.STANDARD_MILEAGE : user.getVehicleCostMethod();
        return new AccountSettingsResponse(method, effectiveMileageRate(user), defaultMileageRate, mileageRateYear,
                user.getTimeZone() == null ? AppUser.DEFAULT_TIME_ZONE : user.getTimeZone(),
                user.getRemindBeforeMinutes(), user.isRemindConfirm(),
                user.getCalendarToken() == null ? null : "/calendar/" + user.getCalendarToken() + ".ics");
    }

    private AppUser user(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalStateException("Signed-in account could not be found."));
    }
}
