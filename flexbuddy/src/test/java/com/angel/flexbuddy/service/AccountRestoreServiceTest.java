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
import com.angel.flexbuddy.dto.BackupShift;
import com.angel.flexbuddy.dto.CreateShiftRequest;
import com.angel.flexbuddy.dto.RestoreMode;
import com.angel.flexbuddy.dto.RestorePreviewResponse;
import com.angel.flexbuddy.dto.RestoreRequest;
import com.angel.flexbuddy.dto.RestoreResult;
import com.angel.flexbuddy.exception.InvalidBackupException;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.ShiftRepository;

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

    private AccountRestoreService service;
    private AppUser owner;
    private AccountBackupFile backup;

    @BeforeEach
    void setUp() throws Exception {
        service = new AccountRestoreService(objectMapper, validator, shiftRepository, userRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
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

    private List<Shift> shifts(RestoreResult ignored) {
        ArgumentCaptor<List<Shift>> captor = listCaptor();
        verify(shiftRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<List<Shift>> listCaptor() {
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
