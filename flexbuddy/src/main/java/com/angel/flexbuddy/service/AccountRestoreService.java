package com.angel.flexbuddy.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.angel.flexbuddy.dto.AccountBackupFile;
import com.angel.flexbuddy.dto.BackupShift;
import com.angel.flexbuddy.dto.BackupExpense;
import com.angel.flexbuddy.dto.CreateShiftRequest;
import com.angel.flexbuddy.dto.RestoreMode;
import com.angel.flexbuddy.dto.RestorePreviewResponse;
import com.angel.flexbuddy.dto.RestoreProblem;
import com.angel.flexbuddy.dto.RestoreRequest;
import com.angel.flexbuddy.dto.RestoreResult;
import com.angel.flexbuddy.exception.InvalidBackupException;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.model.Expense;
import com.angel.flexbuddy.model.ExpenseCategory;
import com.angel.flexbuddy.model.VehicleCostMethod;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.ShiftRepository;
import com.angel.flexbuddy.repository.ExpenseRepository;
import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

@Service
public class AccountRestoreService {

    private static final String SESSION_KEY = "flexbuddy.restore.staged";
    private static final int MAX_SHIFTS = 10_000;
    private static final int MAX_RECORDS = 20_000;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final ShiftRepository shiftRepository;
    private final AppUserRepository userRepository;
    private final Clock clock;
    private final ExpenseRepository expenseRepository;

    public AccountRestoreService(ObjectMapper objectMapper, Validator validator, ShiftRepository shiftRepository,
            AppUserRepository userRepository, Clock clock, ExpenseRepository expenseRepository) {
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.shiftRepository = shiftRepository;
        this.userRepository = userRepository;
        this.clock = clock;
        this.expenseRepository = expenseRepository;
    }

    public RestorePreviewResponse preview(String email, MultipartFile backup, HttpSession session) {
        if (backup == null || backup.isEmpty()) throw new InvalidBackupException("Choose a FlexBuddy backup file.");
        if (backup.getSize() > 5 * 1024 * 1024) throw new InvalidBackupException("The backup must be 5 MB or smaller.");

        AccountBackupFile file;
        try {
            file = objectMapper.readValue(backup.getInputStream(), AccountBackupFile.class);
        } catch (IOException | RuntimeException exception) {
            throw new InvalidBackupException("This file is not a readable FlexBuddy backup.", exception);
        }
        validateHeader(file);

        List<RestoreProblem> problems = validateRows(file.shifts());
        problems.addAll(validateExpenses(file));
        List<Shift> owned = shiftRepository.findAllIncludingDeleted(email);
        Set<String> liveKeys = keys(owned.stream().filter(shift -> shift.getDeletedAt() == null).toList());
        Set<String> trashedKeys = keys(owned.stream().filter(shift -> shift.getDeletedAt() != null).toList());
        int alreadyPresent = 0;
        int inRecentlyDeleted = 0;
        int newShifts = 0;
        int newDeletedShifts = 0;
        int duplicateInBackup = 0;
        Set<Integer> invalidIndexes = new HashSet<>();
        problems.forEach(problem -> invalidIndexes.add(problem.index()));
        Set<String> backupKeys = new HashSet<>();
        Set<String> activeBackupKeys = new HashSet<>();
        Map<String, Integer> backupOccurrences = new HashMap<>();
        for (int index = 0; index < file.shifts().size(); index++) {
            BackupShift shift = file.shifts().get(index);
            if (invalidIndexes.contains(index)) continue;
            String key = key(shift);
            backupKeys.add(key);
            backupOccurrences.merge(key, 1, Integer::sum);
            if (shift.deletedAt() == null) activeBackupKeys.add(key);
        }
        for (String key : backupKeys) {
            duplicateInBackup += backupOccurrences.get(key) - 1;
            if (liveKeys.contains(key)) alreadyPresent++;
            else if (trashedKeys.contains(key)) inRecentlyDeleted++;
            else if (activeBackupKeys.contains(key)) newShifts++;
            else newDeletedShifts++;
        }

        String token = UUID.randomUUID().toString();
        session.setAttribute(SESSION_KEY, new StagedBackup(token, email.toLowerCase(Locale.ROOT), file,
                Instant.now(clock).plus(15, ChronoUnit.MINUTES)));
        int deleted = (int) file.shifts().stream().filter(shift -> shift != null && shift.deletedAt() != null).count();
        List<Expense> ownedExpenses = expenseRepository.findAllIncludingDeleted(email);
        Set<String> existingExpenseKeys = expenseKeys(ownedExpenses);
        Set<String> backupExpenseKeys = new HashSet<>();
        int duplicateExpenses = 0;
        int newExpenses = 0;
        for (BackupExpense expense : file.expenses()) {
            if (expense == null) continue;
            String expenseKey = expenseKey(expense);
            if (!backupExpenseKeys.add(expenseKey)) duplicateExpenses++;
            else if (!existingExpenseKeys.contains(expenseKey)) newExpenses++;
        }
        int deletedExpenses = (int) file.expenses().stream()
                .filter(expense -> expense != null && expense.deletedAt() != null).count();
        String sourceEmail = file.account() == null ? null : file.account().email();
        return new RestorePreviewResponse(
                token, file.format(), file.version(), file.exportedAt(), sourceEmail,
                sourceEmail != null && sourceEmail.equalsIgnoreCase(email), file.shifts().size(), newShifts,
                newDeletedShifts, alreadyPresent, inRecentlyDeleted, duplicateInBackup,
                invalidIndexes.size(), deleted, problems,
                liveKeys.size(), file.expenses().size(), newExpenses, duplicateExpenses, deletedExpenses
        );
    }

    @Transactional
    public RestoreResult restore(String email, RestoreRequest request, HttpSession session) {
        Object value = session.getAttribute(SESSION_KEY);
        if (!(value instanceof StagedBackup staged) || !staged.token().equals(request.token())) {
            throw new InvalidBackupException("This restore preview has expired. Upload the backup again.");
        }
        if (!staged.email().equals(email.toLowerCase(Locale.ROOT))
                || !staged.expiresAt().isAfter(Instant.now(clock))) {
            session.removeAttribute(SESSION_KEY);
            throw new InvalidBackupException("This restore preview has expired. Upload the backup again.");
        }
        if (request.mode() == RestoreMode.REPLACE && !request.acknowledgeReplace()) {
            throw new InvalidBackupException("Confirm that Replace will move current shifts to Recently deleted.");
        }

        AccountBackupFile file = staged.file();
        List<RestoreProblem> problems = validateRows(file.shifts());
        problems.addAll(validateExpenses(file));
        if (problems.stream().anyMatch(problem -> "settings".equals(problem.field()))) {
            throw new InvalidBackupException("The backup contains invalid expense settings.");
        }
        if (request.mode() == RestoreMode.REPLACE && !problems.isEmpty()) {
            throw new InvalidBackupException("Replace cannot continue because the backup contains invalid shifts.");
        }

        AppUser owner = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalStateException("Signed-in account could not be found."));
        Set<Integer> invalidIndexes = new HashSet<>();
        problems.stream().filter(problem -> problem.index() < file.shifts().size())
                .forEach(problem -> invalidIndexes.add(problem.index()));
        List<Shift> currentShifts = shiftRepository.findAllIncludingDeleted(email);
        Map<String, Shift> existingByKey = new HashMap<>();
        if (request.mode() == RestoreMode.MERGE) currentShifts.forEach(shift -> existingByKey.putIfAbsent(
                key(shift.getDate(), shift.getStartTime(), shift.getStation()), shift));
        Set<String> existing = new HashSet<>(existingByKey.keySet());
        String batch = request.mode() == RestoreMode.REPLACE ? UUID.randomUUID().toString() : null;
        String insertedBatch = batch == null ? null : insertedBatch(batch);
        if (batch != null) {
            expenseRepository.softDeleteAll(email, Instant.now(clock), batch);
            shiftRepository.softDeleteAll(email, Instant.now(clock), batch);
        }

        List<Shift> insert = new ArrayList<>();
        Map<Long, Shift> restoredShiftLinks = new HashMap<>();
        int skipped = 0;
        for (int index = 0; index < file.shifts().size(); index++) {
            BackupShift source = file.shifts().get(index);
            if (invalidIndexes.contains(index) || (source.deletedAt() != null && !request.includeDeleted())) {
                skipped++;
                continue;
            }
            String sourceKey = key(source);
            if (!existing.add(sourceKey)) {
                if (source.id() != null && existingByKey.containsKey(sourceKey)) restoredShiftLinks.put(source.id(), existingByKey.get(sourceKey));
                skipped++;
                continue;
            }
            Shift entity = toEntity(source, owner, insertedBatch);
            insert.add(entity);
            if (source.id() != null) restoredShiftLinks.put(source.id(), entity);
        }
        shiftRepository.saveAll(insert);
        Set<String> existingExpenses = request.mode() == RestoreMode.MERGE
                ? expenseKeys(expenseRepository.findAllIncludingDeleted(email)) : new HashSet<>();
        List<Expense> expenseInsert = new ArrayList<>();
        int expensesSkipped = 0;
        for (int index = 0; index < file.expenses().size(); index++) {
            BackupExpense source = file.expenses().get(index);
            int problemIndex = file.shifts().size() + index;
            boolean invalid = problems.stream().anyMatch(problem -> problem.index() == problemIndex);
            if (invalid || (source.deletedAt() != null && !request.includeDeleted())
                    || !existingExpenses.add(expenseKey(source))) {
                expensesSkipped++;
                continue;
            }
            expenseInsert.add(toExpenseEntity(source, owner, restoredShiftLinks.get(source.shiftBackupId()), insertedBatch));
        }
        expenseRepository.saveAll(expenseInsert);
        if (file.version() >= 2 && file.settings() != null) {
            try {
                owner.setVehicleCostMethod(VehicleCostMethod.valueOf(file.settings().vehicleCostMethod()));
                owner.setMileageRate(file.settings().mileageRate() == null ? null : new BigDecimal(file.settings().mileageRate()));
                userRepository.save(owner);
            } catch (RuntimeException exception) {
                throw new InvalidBackupException("The backup contains invalid expense settings.", exception);
            }
        }
        session.removeAttribute(SESSION_KEY);
        return new RestoreResult(insert.size(), skipped, expenseInsert.size(), expensesSkipped, batch);
    }

    @Transactional
    public java.util.Map<String, Integer> undoReplace(String email, String batch) {
        int removedExpenses = expenseRepository.deleteBatch(email, insertedBatch(batch));
        int removed = shiftRepository.deleteBatch(email, insertedBatch(batch));
        int restoredExpenses = expenseRepository.restoreBatch(email, batch);
        int restored = shiftRepository.restoreBatch(email, batch);
        return java.util.Map.of("removed", removed, "restored", restored,
                "expensesRemoved", removedExpenses, "expensesRestored", restoredExpenses);
    }

    private Shift toEntity(BackupShift source, AppUser owner, String batch) {
        Shift shift = new Shift();
        shift.setOwner(owner);
        shift.setStation(source.station().trim());
        shift.setDate(source.date());
        shift.setStartTime(source.startTime());
        shift.setEndTime(source.endTime());
        shift.setBasePay(new BigDecimal(source.basePay()));
        shift.setTips(new BigDecimal(source.tips()));
        shift.setMiles(source.miles() == null || source.miles().isBlank() ? null : new BigDecimal(source.miles()));
        Instant now = Instant.now(clock);
        shift.setCreatedAt(source.createdAt() == null ? now : source.createdAt());
        shift.setUpdatedAt(source.updatedAt() == null ? shift.getCreatedAt() : source.updatedAt());
        shift.setDeletedAt(source.deletedAt());
        shift.setDeleteBatch(batch);
        return shift;
    }

    private Expense toExpenseEntity(BackupExpense source, AppUser owner, Shift shift, String batch) {
        Expense expense = new Expense();
        expense.setOwner(owner);
        expense.setShift(shift);
        expense.setDate(source.date());
        expense.setCategory(ExpenseCategory.valueOf(source.category()));
        expense.setAmount(new BigDecimal(source.amount()));
        expense.setNote(source.note());
        Instant now = Instant.now(clock);
        expense.setCreatedAt(source.createdAt() == null ? now : source.createdAt());
        expense.setUpdatedAt(source.updatedAt() == null ? expense.getCreatedAt() : source.updatedAt());
        expense.setDeletedAt(source.deletedAt());
        expense.setDeleteBatch(batch);
        return expense;
    }

    private String insertedBatch(String replaceBatch) {
        return UUID.nameUUIDFromBytes(("flexbuddy-restore:" + replaceBatch).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private void validateHeader(AccountBackupFile file) {
        if (file == null) throw new InvalidBackupException("This file is not a readable FlexBuddy backup.");
        if (!"flexbuddy-backup".equals(file.format())) throw new InvalidBackupException("This is not a FlexBuddy backup file.");
        if (file.version() > 2) throw new InvalidBackupException("This backup was created by a newer FlexBuddy version.");
        if (file.version() < 1) throw new InvalidBackupException("This backup version is not supported.");
        if (file.shifts().size() > MAX_SHIFTS) throw new InvalidBackupException("A backup can contain at most 10,000 shifts.");
        if (file.shifts().size() + file.expenses().size() > MAX_RECORDS) {
            throw new InvalidBackupException("A backup can contain at most 20,000 shifts and expenses.");
        }
    }

    private List<RestoreProblem> validateExpenses(AccountBackupFile file) {
        List<RestoreProblem> problems = new ArrayList<>();
        Set<Long> shiftIds = new HashSet<>();
        file.shifts().stream().filter(shift -> shift != null && shift.id() != null)
                .forEach(shift -> shiftIds.add(shift.id()));
        for (int index = 0; index < file.expenses().size(); index++) {
            BackupExpense expense = file.expenses().get(index);
            int problemIndex = file.shifts().size() + index;
            if (expense == null) {
                problems.add(new RestoreProblem(problemIndex, "expense", "Expense entry is missing."));
                continue;
            }
            if (expense.date() == null) problems.add(new RestoreProblem(problemIndex, "date", "must not be null"));
            try { ExpenseCategory.valueOf(expense.category()); }
            catch (RuntimeException exception) { problems.add(new RestoreProblem(problemIndex, "category", "must be a supported category")); }
            BigDecimal amount = parseMoney(expense.amount(), problemIndex, "amount", problems);
            if (amount != null && amount.signum() <= 0) problems.add(new RestoreProblem(problemIndex, "amount", "must be greater than 0"));
            if (amount != null && (amount.scale() > 2 || Math.max(0, amount.precision() - amount.scale()) > 10)) {
                problems.add(new RestoreProblem(problemIndex, "amount", "must have at most 10 whole digits and 2 decimal places"));
            }
            if (expense.note() != null && expense.note().length() > 255) problems.add(new RestoreProblem(problemIndex, "note", "must be 255 characters or fewer"));
            if (expense.shiftBackupId() != null && !shiftIds.contains(expense.shiftBackupId())) {
                problems.add(new RestoreProblem(problemIndex, "shiftBackupId", "does not reference a shift in this backup"));
            }
        }
        if (file.settings() != null) {
            try {
                VehicleCostMethod.valueOf(file.settings().vehicleCostMethod());
                if (file.settings().mileageRate() != null) {
                    BigDecimal rate = new BigDecimal(file.settings().mileageRate());
                    if (rate.signum() < 0 || rate.compareTo(new BigDecimal("5.000")) > 0 || rate.scale() > 3) {
                        throw new IllegalArgumentException();
                    }
                }
            } catch (RuntimeException exception) {
                problems.add(new RestoreProblem(file.shifts().size() + file.expenses().size(),
                        "settings", "vehicle cost settings are invalid"));
            }
        }
        return problems;
    }

    private List<RestoreProblem> validateRows(List<BackupShift> shifts) {
        List<RestoreProblem> problems = new ArrayList<>();
        for (int index = 0; index < shifts.size(); index++) {
            BackupShift shift = shifts.get(index);
            if (shift == null) {
                problems.add(new RestoreProblem(index, "shift", "Shift entry is missing."));
                continue;
            }
            BigDecimal basePay = parseMoney(shift.basePay(), index, "basePay", problems);
            BigDecimal tips = parseMoney(shift.tips(), index, "tips", problems);
            BigDecimal miles = parseOptionalDecimal(shift.miles(), index, "miles", problems);
            CreateShiftRequest request = new CreateShiftRequest(
                    shift.station(), shift.date(), shift.startTime(), shift.endTime(), basePay, tips, miles);
            for (ConstraintViolation<CreateShiftRequest> violation : validator.validate(request)) {
                problems.add(new RestoreProblem(index, violation.getPropertyPath().toString(), violation.getMessage()));
            }
        }
        return problems;
    }

    private BigDecimal parseMoney(String value, int index, String field, List<RestoreProblem> problems) {
        try {
            return value == null ? null : new BigDecimal(value);
        } catch (NumberFormatException exception) {
            problems.add(new RestoreProblem(index, field, "must be a valid amount"));
            return null;
        }
    }

    private BigDecimal parseOptionalDecimal(String value, int index, String field, List<RestoreProblem> problems) {
        if (value == null || value.isBlank()) return null;
        return parseMoney(value, index, field, problems);
    }

    private Set<String> keys(List<Shift> shifts) {
        Set<String> result = new HashSet<>();
        shifts.forEach(shift -> result.add(key(shift.getDate(), shift.getStartTime(), shift.getStation())));
        return result;
    }

    private Set<String> expenseKeys(List<Expense> expenses) {
        Set<String> result = new HashSet<>();
        expenses.forEach(expense -> result.add(expenseKey(expense.getDate(), expense.getCategory().name(),
                expense.getAmount().toPlainString(), expense.getNote())));
        return result;
    }

    private String expenseKey(BackupExpense expense) {
        return expenseKey(expense.date(), expense.category(), expense.amount(), expense.note());
    }

    private String expenseKey(java.time.LocalDate date, String category, String amount, String note) {
        String normalizedAmount;
        try { normalizedAmount = new BigDecimal(amount).stripTrailingZeros().toPlainString(); }
        catch (RuntimeException exception) { normalizedAmount = String.valueOf(amount); }
        return date + "|" + category + "|" + normalizedAmount + "|" + (note == null ? "" : note.trim());
    }

    private String key(BackupShift shift) { return key(shift.date(), shift.startTime(), shift.station()); }
    private String key(java.time.LocalDate date, java.time.LocalTime start, String station) {
        return date + "|" + start + "|" + (station == null ? "" : station.trim().toLowerCase(Locale.ROOT));
    }

    record StagedBackup(String token, String email, AccountBackupFile file, Instant expiresAt)
            implements java.io.Serializable {
    }
}
