package com.angel.flexbuddy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.SerializationUtils;

import com.angel.flexbuddy.dto.AccountBackupFile;
import com.angel.flexbuddy.dto.BackupAccount;
import com.angel.flexbuddy.dto.BackupCounts;
import com.angel.flexbuddy.dto.BackupExpense;
import com.angel.flexbuddy.dto.BackupSettings;
import com.angel.flexbuddy.dto.BackupShift;
import com.angel.flexbuddy.dto.CreateShiftRequest;
import com.angel.flexbuddy.dto.RestoreMode;
import com.angel.flexbuddy.dto.RestorePreviewResponse;
import com.angel.flexbuddy.dto.RestoreRequest;
import com.angel.flexbuddy.dto.RestoreResult;
import com.angel.flexbuddy.exception.InvalidBackupException;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.Expense;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.model.VehicleCostMethod;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.ShiftRepository;
import com.angel.flexbuddy.repository.ExpenseRepository;

import jakarta.validation.Validator;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AccountRestoreServiceTest {

    private static final String EMAIL = "angel@example.com";
    private static final Instant NOW = Instant.parse("2026-09-11T12:00:00Z");

    @Mock ObjectMapper objectMapper;
    @Mock Validator validator;
    @Mock ShiftRepository shiftRepository;
    @Mock AppUserRepository userRepository;
    @Mock ExpenseRepository expenseRepository;
    @Mock com.angel.flexbuddy.repository.TaxPaymentRepository taxPaymentRepository;
    @Mock com.angel.flexbuddy.repository.PayoutDepositRepository payoutDepositRepository;

    private AccountRestoreService service;
    private AppUser owner;
    private AccountBackupFile backup;

    @BeforeEach
    void setUp() throws Exception {
        service = new AccountRestoreService(objectMapper, validator, shiftRepository, userRepository,
                Clock.fixed(NOW, ZoneOffset.UTC), expenseRepository, taxPaymentRepository, payoutDepositRepository);
        owner = new AppUser("Angel", EMAIL, "hash");
        owner.setId(1L);
        BackupShift existing = backupShift("VEA7", null);
        BackupShift fresh = backupShift("BDL4", null);
        BackupShift deleted = backupShift("TRASH", Instant.parse("2026-09-10T00:00:00Z"));
        backup = new AccountBackupFile("flexbuddy-backup", 1, NOW, "1.0",
                new BackupAccount("Angel", EMAIL, Instant.parse("2026-01-01T00:00:00Z")),
                List.of(existing, fresh, deleted), new BackupCounts(2, 1));
        lenient().when(objectMapper.readValue(any(InputStream.class), eq(AccountBackupFile.class))).thenReturn(backup);
        lenient().when(validator.validate(any(CreateShiftRequest.class))).thenReturn(Collections.emptySet());
        lenient().when(shiftRepository.findAllIncludingDeleted(EMAIL)).thenReturn(List.of(entity("VEA7")));
        lenient().when(expenseRepository.findAllIncludingDeleted(EMAIL)).thenReturn(List.of());
        lenient().when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(owner));
    }

    @Test
    void mergeAddsOnlyNewActiveShiftsAndSkipsDuplicatesAndTrashByDefault() {
        MockHttpSession session = new MockHttpSession();
        RestorePreviewResponse preview = service.preview(EMAIL, upload(), session);

        RestoreResult result = service.restore(EMAIL,
                new RestoreRequest(preview.token(), RestoreMode.MERGE, false, false), session);

        assertThat(preview.total()).isEqualTo(3);
        assertThat(preview.alreadyPresent()).isEqualTo(1);
        assertThat(preview.inRecentlyDeleted()).isZero();
        assertThat(preview.newShifts()).isEqualTo(1);
        assertThat(preview.newDeletedShifts()).isEqualTo(1);
        assertThat(result.inserted()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(2);
        assertThat(result.batchId()).isNull();
        ArgumentCaptor<List<Shift>> shifts = listCaptor();
        verify(shiftRepository).saveAll(shifts.capture());
        assertThat(shifts.getValue()).extracting(Shift::getStation).containsExactly("BDL4");
        assertThat(shifts.getValue().getFirst().getOwner()).isSameAs(owner);
    }

    @Test
    void restoreAddsRecordedPayoutsForDatesWithNoneAndSkipsImpossibleOnes() throws Exception {
        AccountBackupFile withPayouts = new AccountBackupFile("flexbuddy-backup", 4, NOW, "1.0",
                new BackupAccount("Angel", EMAIL, Instant.parse("2026-01-01T00:00:00Z")),
                backup.shifts(), List.of(), null, new BackupCounts(2, 1), List.of(),
                List.of(new com.angel.flexbuddy.dto.BackupPayout(java.time.LocalDate.of(2026, 9, 11), "84.5", " Chase "),
                        new com.angel.flexbuddy.dto.BackupPayout(java.time.LocalDate.of(2026, 9, 8), "70.00", null),
                        new com.angel.flexbuddy.dto.BackupPayout(java.time.LocalDate.of(2026, 9, 4), "-3.00", null),
                        new com.angel.flexbuddy.dto.BackupPayout(null, "10.00", null)));
        when(objectMapper.readValue(any(InputStream.class), eq(AccountBackupFile.class))).thenReturn(withPayouts);
        com.angel.flexbuddy.model.PayoutDeposit recorded = new com.angel.flexbuddy.model.PayoutDeposit();
        recorded.setPayoutDate(java.time.LocalDate.of(2026, 9, 8));
        recorded.setAmount(new BigDecimal("68.00"));
        when(payoutDepositRepository.findByOwnerEmailIgnoreCaseOrderByPayoutDateAsc(EMAIL)).thenReturn(List.of(recorded));
        MockHttpSession session = new MockHttpSession();
        RestorePreviewResponse preview = service.preview(EMAIL, upload(), session);

        service.restore(EMAIL, new RestoreRequest(preview.token(), RestoreMode.MERGE, false, false), session);

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<List<com.angel.flexbuddy.model.PayoutDeposit>> saved = (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
        verify(payoutDepositRepository).saveAll(saved.capture());
        assertThat(saved.getValue()).singleElement().satisfies(deposit -> {
            assertThat(deposit.getPayoutDate()).isEqualTo(java.time.LocalDate.of(2026, 9, 11));
            assertThat(deposit.getAmount()).isEqualByComparingTo("84.50");
            assertThat(deposit.getNote()).isEqualTo("Chase");
            assertThat(deposit.getOwner()).isSameAs(owner);
            assertThat(deposit.getCreatedAt()).isEqualTo(NOW);
        });
    }

    @Test
    void replaceMovesCurrentHistoryToOneBatchAndTagsNewRowsForUndo() {
        MockHttpSession session = new MockHttpSession();
        RestorePreviewResponse preview = service.preview(EMAIL, upload(), session);

        RestoreResult result = service.restore(EMAIL,
                new RestoreRequest(preview.token(), RestoreMode.REPLACE, false, true), session);

        assertThat(result.inserted()).isEqualTo(2);
        assertThat(result.batchId()).isNotBlank();
        verify(shiftRepository).softDeleteAll(EMAIL, NOW, result.batchId());
        assertThat(shifts(result)).allSatisfy(shift ->
                assertThat(shift.getCreatedAt()).isEqualTo(Instant.parse("2026-09-01T00:00:00Z")));
        ArgumentCaptor<List<Shift>> shifts = listCaptor();
        verify(shiftRepository).saveAll(shifts.capture());
        assertThat(shifts.getValue()).allSatisfy(shift -> {
            assertThat(shift.getDeleteBatch()).isNotBlank().isNotEqualTo(result.batchId());
            assertThat(shift.getDeletedAt()).isNull();
        });
        assertThat(shifts.getValue()).extracting(Shift::getDeleteBatch)
                .containsOnly(shifts.getValue().getFirst().getDeleteBatch());
    }

    @Test
    void undoReplaceRemovesInsertedRowsThenRestoresTheOriginalBatch() {
        when(shiftRepository.deleteBatch(eq(EMAIL), any(String.class))).thenReturn(2);
        when(shiftRepository.restoreBatch(EMAIL, "batch")).thenReturn(4);

        var result = service.undoReplace(EMAIL, "batch");

        assertThat(result).containsEntry("removed", 2).containsEntry("restored", 4);
    }

    @Test
    void aBackupRowMatchingATrashedShiftIsReportedSeparatelyAndSkippedByMerge() {
        Shift trashed = entity("BDL4");
        trashed.setDeletedAt(Instant.parse("2026-09-09T00:00:00Z"));
        when(shiftRepository.findAllIncludingDeleted(EMAIL)).thenReturn(List.of(entity("VEA7"), trashed));
        MockHttpSession session = new MockHttpSession();

        RestorePreviewResponse preview = service.preview(EMAIL, upload(), session);
        RestoreResult result = service.restore(EMAIL,
                new RestoreRequest(preview.token(), RestoreMode.MERGE, false, false), session);

        assertThat(preview.alreadyPresent()).isEqualTo(1);
        assertThat(preview.inRecentlyDeleted()).isEqualTo(1);
        assertThat(preview.newShifts()).isZero();
        assertThat(result.inserted()).isZero();
        verify(shiftRepository).saveAll(List.of());
    }

    @Test
    void duplicateRowsInsideTheBackupAreReportedWithoutBeingCalledRecentlyDeleted() throws Exception {
        BackupShift duplicate = backupShift("BDL4", null);
        backup = new AccountBackupFile("flexbuddy-backup", 1, NOW, "1.0",
                new BackupAccount("Angel", EMAIL, Instant.parse("2026-01-01T00:00:00Z")),
                List.of(duplicate, duplicate), new BackupCounts(2, 0));
        when(objectMapper.readValue(any(InputStream.class), eq(AccountBackupFile.class))).thenReturn(backup);
        when(shiftRepository.findAllIncludingDeleted(EMAIL)).thenReturn(List.of());
        MockHttpSession session = new MockHttpSession();

        RestorePreviewResponse preview = service.preview(EMAIL, upload(), session);
        RestoreResult result = service.restore(EMAIL,
                new RestoreRequest(preview.token(), RestoreMode.MERGE, false, false), session);

        assertThat(preview.newShifts()).isEqualTo(1);
        assertThat(preview.duplicateInBackup()).isEqualTo(1);
        assertThat(preview.inRecentlyDeleted()).isZero();
        assertThat(preview.alreadyPresent()).isZero();
        assertThat(result.inserted()).isEqualTo(1);
        assertThat(result.skipped()).isEqualTo(1);
    }

    @Test
    void previewKeepsOneStagedBackupPerSessionAndClearsItAfterARestore() {
        MockHttpSession session = new MockHttpSession();
        RestorePreviewResponse first = service.preview(EMAIL, upload(), session);
        RestorePreviewResponse second = service.preview(EMAIL, upload(), session);

        assertThat(Collections.list(session.getAttributeNames())).hasSize(1);
        assertThatThrownBy(() -> service.restore(EMAIL,
                new RestoreRequest(first.token(), RestoreMode.MERGE, false, false), session))
                .isInstanceOf(InvalidBackupException.class)
                .hasMessageContaining("expired");
        assertThat(Collections.list(session.getAttributeNames())).hasSize(1);

        service.restore(EMAIL, new RestoreRequest(second.token(), RestoreMode.MERGE, false, false), session);

        assertThat(Collections.list(session.getAttributeNames())).isEmpty();
    }

    @Test
    void theStagedBackupSurvivesSessionSerialisation() {
        MockHttpSession session = new MockHttpSession();
        service.preview(EMAIL, upload(), session);
        Object staged = session.getAttribute(Collections.list(session.getAttributeNames()).getFirst());

        Object copy = SerializationUtils.deserialize(SerializationUtils.serialize(staged));

        assertThat(copy).isEqualTo(staged);
    }

    @Test
    void versionTwoRestorePreservesMileageExpenseLinksAndSettings() throws Exception {
        BackupShift sourceShift = new BackupShift(42L, "BDL4", LocalDate.of(2026, 9, 7),
                LocalTime.of(3, 30), LocalTime.of(8, 0), "157.50", "0.00", "28.4",
                NOW, NOW, null);
        BackupExpense sourceExpense = new BackupExpense(90L, LocalDate.of(2026, 9, 7), "TOLL",
                "6.25", "Bridge", 42L, NOW, NOW, null);
        backup = new AccountBackupFile("flexbuddy-backup", 2, NOW, "2.0",
                new BackupAccount("Angel", EMAIL, NOW), List.of(sourceShift), List.of(sourceExpense),
                new BackupSettings("ACTUAL_EXPENSES", "0.655"), new BackupCounts(1, 0, 1, 0));
        when(objectMapper.readValue(any(InputStream.class), eq(AccountBackupFile.class))).thenReturn(backup);
        when(shiftRepository.findAllIncludingDeleted(EMAIL)).thenReturn(List.of());
        MockHttpSession session = new MockHttpSession();

        RestorePreviewResponse preview = service.preview(EMAIL, upload(), session);
        RestoreResult result = service.restore(EMAIL,
                new RestoreRequest(preview.token(), RestoreMode.MERGE, false, false), session);

        assertThat(result.inserted()).isEqualTo(1);
        assertThat(result.expensesInserted()).isEqualTo(1);
        ArgumentCaptor<List<Shift>> shiftCaptor = listCaptor();
        verify(shiftRepository).saveAll(shiftCaptor.capture());
        Shift restoredShift = shiftCaptor.getValue().getFirst();
        assertThat(restoredShift.getMiles()).isEqualByComparingTo("28.4");
        ArgumentCaptor<List<Expense>> expenseCaptor = expenseListCaptor();
        verify(expenseRepository).saveAll(expenseCaptor.capture());
        assertThat(expenseCaptor.getValue()).singleElement().satisfies(expense -> {
            assertThat(expense.getShift()).isSameAs(restoredShift);
            assertThat(expense.getAmount()).isEqualByComparingTo("6.25");
            assertThat(expense.getOwner()).isSameAs(owner);
        });
        assertThat(owner.getVehicleCostMethod()).isEqualTo(VehicleCostMethod.ACTUAL_EXPENSES);
        assertThat(owner.getMileageRate()).isEqualByComparingTo("0.655");
        verify(userRepository).save(owner);
    }

    @Test
    void versionFourRestoreKeepsBlockDetailsAndFlagsReadingsThatRunBackwards() throws Exception {
        BackupShift timed = new BackupShift(42L, "BDL4", LocalDate.of(2026, 9, 7), LocalTime.of(3, 30),
                LocalTime.of(8, 0), "157.50", "0.00", "23.4", NOW, NOW, null, "COMPLETED", NOW,
                LocalTime.of(3, 40), LocalTime.of(7, 10), "45210.4", "45233.8", 42, 60, 1, false);
        BackupShift backwards = new BackupShift(43L, "BDL4", LocalDate.of(2026, 9, 8), LocalTime.of(3, 30),
                LocalTime.of(8, 0), "157.50", "0.00", null, NOW, NOW, null, "COMPLETED", NOW,
                null, null, "45300.0", "45250.0", null, null, null, false);
        backup = new AccountBackupFile("flexbuddy-backup", 4, NOW, "4.0",
                new BackupAccount("Angel", EMAIL, NOW), List.of(timed, backwards), List.of(), null,
                new BackupCounts(2, 0, 0, 0));
        when(objectMapper.readValue(any(InputStream.class), eq(AccountBackupFile.class))).thenReturn(backup);
        when(shiftRepository.findAllIncludingDeleted(EMAIL)).thenReturn(List.of());
        MockHttpSession session = new MockHttpSession();

        RestorePreviewResponse preview = service.preview(EMAIL, upload(), session);
        service.restore(EMAIL, new RestoreRequest(preview.token(), RestoreMode.MERGE, false, false), session);

        assertThat(preview.problems()).singleElement().satisfies(problem -> {
            assertThat(problem.index()).isEqualTo(1);
            assertThat(problem.field()).isEqualTo("odometerEnd");
        });
        assertThat(shifts(null)).singleElement().satisfies(shift -> {
            assertThat(shift.getActualStart()).isEqualTo(LocalTime.of(3, 40));
            assertThat(shift.getActualEnd()).isEqualTo(LocalTime.of(7, 10));
            assertThat(shift.getOdometerStart()).isEqualByComparingTo("45210.4");
            assertThat(shift.getOdometerEnd()).isEqualByComparingTo("45233.8");
        });
    }

    private List<Shift> shifts(RestoreResult ignored) {
        ArgumentCaptor<List<Shift>> captor = listCaptor();
        verify(shiftRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<List<Shift>> listCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<List<Expense>> expenseListCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    }

    private MockMultipartFile upload() {
        return new MockMultipartFile("backup", "backup.json", "application/json", "{}".getBytes());
    }

    private BackupShift backupShift(String station, Instant deletedAt) {
        return new BackupShift(null, station, LocalDate.of(2026, 9, 6), LocalTime.of(9, 0),
                LocalTime.of(13, 0), "100.00", "0.00", Instant.parse("2026-09-01T00:00:00Z"),
                Instant.parse("2026-09-01T00:00:00Z"), deletedAt);
    }

    private Shift entity(String station) {
        return new Shift(9L, station, LocalDate.of(2026, 9, 6), LocalTime.of(9, 0), LocalTime.of(13, 0),
                new BigDecimal("100.00"), BigDecimal.ZERO, owner);
    }
}
