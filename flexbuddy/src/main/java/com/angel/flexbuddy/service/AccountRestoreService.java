package com.angel.flexbuddy.service;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.nio.charset.StandardCharsets;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.angel.flexbuddy.dto.AccountBackupFile;
import com.angel.flexbuddy.dto.BackupShift;
import com.angel.flexbuddy.dto.CreateShiftRequest;
import com.angel.flexbuddy.dto.RestoreMode;
import com.angel.flexbuddy.dto.RestorePreviewResponse;
import com.angel.flexbuddy.dto.RestoreProblem;
import com.angel.flexbuddy.dto.RestoreRequest;
import com.angel.flexbuddy.dto.RestoreResult;
import com.angel.flexbuddy.exception.InvalidBackupException;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.ShiftRepository;
import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

@Service
public class AccountRestoreService {

    private static final String SESSION_PREFIX = "flexbuddy.restore.";
    private static final int MAX_SHIFTS = 10_000;
    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final ShiftRepository shiftRepository;
    private final AppUserRepository userRepository;
    private final Clock clock;

    public AccountRestoreService(ObjectMapper objectMapper, Validator validator, ShiftRepository shiftRepository,
            AppUserRepository userRepository, Clock clock) {
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.shiftRepository = shiftRepository;
        this.userRepository = userRepository;
        this.clock = clock;
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
        Set<String> existingKeys = keys(shiftRepository.findAllByOwnerEmailIgnoreCaseOrderByDateDescStartTimeDesc(email));
        Set<String> seen = new HashSet<>(existingKeys);
        int alreadyPresent = 0;
        int newShifts = 0;
        Set<Integer> invalidIndexes = new HashSet<>();
        problems.forEach(problem -> invalidIndexes.add(problem.index()));
        for (int index = 0; index < file.shifts().size(); index++) {
            BackupShift shift = file.shifts().get(index);
            if (invalidIndexes.contains(index)) continue;
            if (!seen.add(key(shift))) alreadyPresent++; else newShifts++;
        }

        String token = UUID.randomUUID().toString();
        session.setAttribute(SESSION_PREFIX + token,
                new StagedBackup(email.toLowerCase(Locale.ROOT), file, Instant.now(clock).plus(15, ChronoUnit.MINUTES)));
        int deleted = (int) file.shifts().stream().filter(shift -> shift != null && shift.deletedAt() != null).count();
        String sourceEmail = file.account() == null ? null : file.account().email();
        return new RestorePreviewResponse(
                token, file.format(), file.version(), file.exportedAt(), sourceEmail,
                sourceEmail != null && sourceEmail.equalsIgnoreCase(email), file.shifts().size(), newShifts,
                alreadyPresent, invalidIndexes.size(), deleted, problems, existingKeys.size()
        );
    }

    @Transactional
    public RestoreResult restore(String email, RestoreRequest request, HttpSession session) {
        Object value = session.getAttribute(SESSION_PREFIX + request.token());
        if (!(value instanceof StagedBackup staged)
                || !staged.email().equals(email.toLowerCase(Locale.ROOT))
                || !staged.expiresAt().isAfter(Instant.now(clock))) {
            session.removeAttribute(SESSION_PREFIX + request.token());
            throw new InvalidBackupException("This restore preview has expired. Upload the backup again.");
        }
        if (request.mode() == RestoreMode.REPLACE && !request.acknowledgeReplace()) {
            throw new InvalidBackupException("Confirm that Replace will move current shifts to Recently deleted.");
        }

        AccountBackupFile file = staged.file();
        List<RestoreProblem> problems = validateRows(file.shifts());
        if (request.mode() == RestoreMode.REPLACE && !problems.isEmpty()) {
            throw new InvalidBackupException("Replace cannot continue because the backup contains invalid shifts.");
        }

        AppUser owner = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalStateException("Signed-in account could not be found."));
        Set<Integer> invalidIndexes = new HashSet<>();
        problems.forEach(problem -> invalidIndexes.add(problem.index()));
        Set<String> existing = request.mode() == RestoreMode.MERGE
                ? keys(shiftRepository.findAllByOwnerEmailIgnoreCaseOrderByDateDescStartTimeDesc(email))
                : new HashSet<>();
        String batch = request.mode() == RestoreMode.REPLACE ? UUID.randomUUID().toString() : null;
        String insertedBatch = batch == null ? null : insertedBatch(batch);
        if (batch != null) shiftRepository.softDeleteAll(email, Instant.now(clock), batch);

        List<Shift> insert = new ArrayList<>();
        int skipped = 0;
        for (int index = 0; index < file.shifts().size(); index++) {
            BackupShift source = file.shifts().get(index);
            if (invalidIndexes.contains(index) || (source.deletedAt() != null && !request.includeDeleted())) {
                skipped++;
                continue;
            }
            if (!existing.add(key(source))) {
                skipped++;
                continue;
            }
            insert.add(toEntity(source, owner, insertedBatch));
        }
        shiftRepository.saveAll(insert);
        session.removeAttribute(SESSION_PREFIX + request.token());
        return new RestoreResult(insert.size(), skipped, batch);
    }

    @Transactional
    public java.util.Map<String, Integer> undoReplace(String email, String batch) {
        int removed = shiftRepository.deleteBatch(email, insertedBatch(batch));
        int restored = shiftRepository.restoreBatch(email, batch, Instant.now(clock));
        return java.util.Map.of("removed", removed, "restored", restored);
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
        Instant now = Instant.now(clock);
        shift.setCreatedAt(source.createdAt() == null ? now : source.createdAt());
        shift.setUpdatedAt(source.updatedAt() == null ? shift.getCreatedAt() : source.updatedAt());
        shift.setDeletedAt(source.deletedAt());
        shift.setDeleteBatch(batch);
        return shift;
    }

    private String insertedBatch(String replaceBatch) {
        return UUID.nameUUIDFromBytes(("flexbuddy-restore:" + replaceBatch).getBytes(StandardCharsets.UTF_8)).toString();
    }

    private void validateHeader(AccountBackupFile file) {
        if (file == null) throw new InvalidBackupException("This file is not a readable FlexBuddy backup.");
        if (!"flexbuddy-backup".equals(file.format())) throw new InvalidBackupException("This is not a FlexBuddy backup file.");
        if (file.version() > 1) throw new InvalidBackupException("This backup was created by a newer FlexBuddy version.");
        if (file.version() < 1) throw new InvalidBackupException("This backup version is not supported.");
        if (file.shifts().size() > MAX_SHIFTS) throw new InvalidBackupException("A backup can contain at most 10,000 shifts.");
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
            CreateShiftRequest request = new CreateShiftRequest(
                    shift.station(), shift.date(), shift.startTime(), shift.endTime(), basePay, tips);
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

    private Set<String> keys(List<Shift> shifts) {
        Set<String> result = new HashSet<>();
        shifts.forEach(shift -> result.add(key(shift.getDate(), shift.getStartTime(), shift.getStation())));
        return result;
    }

    private String key(BackupShift shift) { return key(shift.date(), shift.startTime(), shift.station()); }
    private String key(java.time.LocalDate date, java.time.LocalTime start, String station) {
        return date + "|" + start + "|" + (station == null ? "" : station.trim().toLowerCase(Locale.ROOT));
    }

    private record StagedBackup(String email, AccountBackupFile file, Instant expiresAt) implements java.io.Serializable {
    }
}
