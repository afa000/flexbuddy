package com.angel.flexbuddy.api;

import java.util.Locale;
import com.angel.flexbuddy.model.AppUser;

public record ApiAccount(String email, String displayName, String language, String timeZone,
        boolean emailVerified, String twoFactorMethod) {
    static ApiAccount of(AppUser user) {
        return new ApiAccount(user.getEmail(), user.getDisplayName(), user.getLanguage(), user.getTimeZone(),
                user.isEmailVerified(), user.getTwoFactorMethod() == null ? null
                        : user.getTwoFactorMethod().name().toLowerCase(Locale.ROOT));
    }
}
