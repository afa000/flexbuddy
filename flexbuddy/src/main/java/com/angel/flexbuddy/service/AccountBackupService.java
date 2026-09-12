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
import com.angel.flexbuddy.dto.BackupExpense;
import com.angel.flexbuddy.dto.BackupSettings;
import com.angel.flexbuddy.model.Expense;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.ShiftRepository;
import com.angel.flexbuddy.repository.ExpenseRepository;

@Service
public class AccountBackupService {

    private final AppUserRepository userRepository;
    private final ShiftRepository shiftRepository;
    private final ExpenseRepository expenseRepository;
    private final Clock clock;
    private final String appVersion;

    public AccountBackupService(AppUserRepository userRepository, ShiftRepository shiftRepository,
            ExpenseRepository expenseRepository, Clock clock,
            @Value("${spring.application.version:0.0.1-SNAPSHOT}") String appVersion) {
        this.userRepository = userRepository;
        this.shiftRepository = shiftRepository;
        this.expenseRepository = expenseRepository;
        this.clock = clock;
        this.appVersion = appVersion;
    }

    @Transactional
    public AccountBackupFile create(String email) {
        AppUser user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalStateException("Signed-in account could not be found."));
        List<Shift> shifts = shiftRepository.findAllIncludingDeleted(email);
        List<Expense> expenses = expenseRepository.findAllIncludingDeleted(email);
        Instant exportedAt = Instant.now(clock);
        user.setLastBackupAt(exportedAt);
        userRepository.save(user);
        int deleted = (int) shifts.stream().filter(shift -> shift.getDeletedAt() != null).count();
        int deletedExpenses = (int) expenses.stream().filter(expense -> expense.getDeletedAt() != null).count();
        return new AccountBackupFile(
                "flexbuddy-backup", 2, exportedAt, appVersion,
                new BackupAccount(user.getDisplayName(), user.getEmail(), user.getCreatedAt()),
                shifts.stream().map(this::toBackupShift).toList(),
                expenses.stream().map(this::toBackupExpense).toList(),
                new BackupSettings((user.getVehicleCostMethod() == null
                        ? com.angel.flexbuddy.model.VehicleCostMethod.STANDARD_MILEAGE : user.getVehicleCostMethod()).name(),
                        user.getMileageRate() == null ? null : user.getMileageRate().toPlainString()),
                new BackupCounts(shifts.size() - deleted, deleted, expenses.size() - deletedExpenses, deletedExpenses)
        );
    }

    private BackupShift toBackupShift(Shift shift) {
        return new BackupShift(
                shift.getId(), shift.getStation(), shift.getDate(), shift.getStartTime(), shift.getEndTime(),
                money(shift.getBasePay()), money(shift.getTips()), decimal(shift.getMiles()), shift.getCreatedAt(), shift.getUpdatedAt(),
                shift.getDeletedAt()
        );
    }

    private BackupExpense toBackupExpense(Expense expense) {
        return new BackupExpense(expense.getId(), expense.getDate(), expense.getCategory().name(), money(expense.getAmount()),
                expense.getNote(), expense.getShift() == null ? null : expense.getShift().getId(), expense.getCreatedAt(),
                expense.getUpdatedAt(), expense.getDeletedAt());
    }

    private String money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private String decimal(BigDecimal value) {
        return value == null ? null : value.stripTrailingZeros().toPlainString();
    }
}
