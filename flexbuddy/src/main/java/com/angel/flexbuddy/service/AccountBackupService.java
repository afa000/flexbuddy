package com.angel.flexbuddy.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.angel.flexbuddy.dto.AccountBackupFile;
import com.angel.flexbuddy.dto.BackupAccount;
import com.angel.flexbuddy.dto.BackupCounts;
import com.angel.flexbuddy.dto.BackupShift;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.ShiftRepository;

@Service
public class AccountBackupService {

    private final AppUserRepository userRepository;
    private final ShiftRepository shiftRepository;
    private final Clock clock;
    private final String appVersion;

    public AccountBackupService(AppUserRepository userRepository, ShiftRepository shiftRepository, Clock clock,
            @Value("${spring.application.version:0.0.1-SNAPSHOT}") String appVersion) {
        this.userRepository = userRepository;
        this.shiftRepository = shiftRepository;
        this.clock = clock;
        this.appVersion = appVersion;
    }

    @Transactional
    public AccountBackupFile create(String email) {
        AppUser user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalStateException("Signed-in account could not be found."));
        List<Shift> shifts = shiftRepository.findAllIncludingDeleted(email);
        Instant exportedAt = Instant.now(clock);
        user.setLastBackupAt(exportedAt);
        userRepository.save(user);
        int deleted = (int) shifts.stream().filter(shift -> shift.getDeletedAt() != null).count();
        return new AccountBackupFile(
                "flexbuddy-backup", 1, exportedAt, appVersion,
                new BackupAccount(user.getDisplayName(), user.getEmail(), user.getCreatedAt()),
                shifts.stream().map(this::toBackupShift).toList(),
                new BackupCounts(shifts.size() - deleted, deleted)
        );
    }

    private BackupShift toBackupShift(Shift shift) {
        return new BackupShift(
                shift.getId(), shift.getStation(), shift.getDate(), shift.getStartTime(), shift.getEndTime(),
                money(shift.getBasePay()), money(shift.getTips()), shift.getCreatedAt(), shift.getUpdatedAt(),
                shift.getDeletedAt()
        );
    }

    private String money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
