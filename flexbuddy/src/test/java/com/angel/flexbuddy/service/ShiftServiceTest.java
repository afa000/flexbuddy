package com.angel.flexbuddy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.angel.flexbuddy.dto.BlockDetailsRequest;
import com.angel.flexbuddy.dto.CreateShiftRequest;
import com.angel.flexbuddy.dto.ShiftFilter;
import com.angel.flexbuddy.dto.ShiftResponse;
import com.angel.flexbuddy.dto.UpdateShiftRequest;
import com.angel.flexbuddy.exception.InvalidFilterException;
import com.angel.flexbuddy.exception.ShiftNotFoundException;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.model.ShiftStatus;
import com.angel.flexbuddy.dto.ShiftStatusRequest;
import com.angel.flexbuddy.exception.InvalidShiftException;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.ShiftRepository;

@ExtendWith(MockitoExtension.class)
class ShiftServiceTest {

    private static final String OWNER_EMAIL = "angel@example.com";

    @Mock ShiftRepository shiftRepository;
    @Mock AppUserRepository userRepository;
    @Mock Clock clock;
    @Mock ExpenseService expenseService;
    @Mock AccountSettingsService settingsService;
    @Spy NetEarningsCalculator netCalculator = new NetEarningsCalculator();
    @Mock UserTimeService userTime;
    @InjectMocks ShiftService shiftService;

    private AppUser owner;

    @BeforeEach
    void setUpOwner() {
        org.mockito.Mockito.lenient().when(clock.instant()).thenReturn(Instant.parse("2026-09-11T12:00:00Z"));
        org.mockito.Mockito.lenient().when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        owner = new AppUser("Angel", OWNER_EMAIL, "password-hash");
        owner.setId(10L);
        org.mockito.Mockito.lenient().when(settingsService.get(OWNER_EMAIL)).thenReturn(
                new com.angel.flexbuddy.dto.AccountSettingsResponse(
                        com.angel.flexbuddy.model.VehicleCostMethod.STANDARD_MILEAGE,
                        new BigDecimal("0.70"), new BigDecimal("0.70"), 2025));
        org.mockito.Mockito.lenient().when(expenseService.findForShifts(eq(OWNER_EMAIL), any())).thenReturn(List.of());
    }

    @Test
    void createShift_assignsTheSignedInOwnerAndMapsDerivedFields() {
        when(userRepository.findByEmailIgnoreCase(OWNER_EMAIL)).thenReturn(Optional.of(owner));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShiftResponse result = shiftService.createShift(OWNER_EMAIL, request());

        ArgumentCaptor<Shift> captor = ArgumentCaptor.forClass(Shift.class);
        verify(shiftRepository).save(captor.capture());
        assertThat(captor.getValue().getOwner()).isSameAs(owner);
        assertThat(result.getTotalPay()).isEqualByComparingTo("155.50");
        assertThat(result.getTimeWorked()).isEqualTo(480);
        assertThat(result.getHourlyRate()).isEqualByComparingTo("19.44");
    }

    @Test
    void getAllShifts_usesTheOwnerScopedFilteredQuery() {
        when(shiftRepository.findFiltered(OWNER_EMAIL, LocalDate.of(1, 1, 1), LocalDate.of(9999, 12, 31), "", "", ShiftStatus.EARNINGS))
                .thenReturn(List.of(shift(1L, "VEA7", LocalDate.of(2026, 9, 6), "120", "10")));

        List<ShiftResponse> result = shiftService.getAllShifts(OWNER_EMAIL);

        assertThat(result).extracting(ShiftResponse::getId).containsExactly(1L);
        verify(shiftRepository).findFiltered(OWNER_EMAIL, LocalDate.of(1, 1, 1), LocalDate.of(9999, 12, 31), "", "", ShiftStatus.EARNINGS);
        verify(shiftRepository, never()).findAll();
    }

    @Test
    void getShifts_passesNormalizedFiltersToRepositoryAndSortsHourlyRateDescending() {
        Shift low = shift(1L, "VEA7", LocalDate.of(2026, 9, 8), "80", "0");
        Shift high = shift(2L, "VEA6", LocalDate.of(2026, 9, 7), "160", "0");
        ShiftFilter filter = ShiftFilter.of(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                " VEA7 ", " vea ", "hourlyRate", "desc");
        when(shiftRepository.findFiltered(OWNER_EMAIL, filter.from(), filter.to(), "VEA7", "vea", ShiftStatus.HISTORY))
                .thenReturn(List.of(low, high));

        List<ShiftResponse> result = shiftService.getShifts(OWNER_EMAIL, filter);

        assertThat(result).extracting(ShiftResponse::getId).containsExactly(2L, 1L);
    }

    @Test
    void getShifts_sortsStationAscendingAndKeepsNewestFirstForTies() {
        Shift olderVea = shift(1L, "VEA7", LocalDate.of(2026, 9, 5), "100", "0");
        Shift bdl = shift(2L, "BDL4", LocalDate.of(2026, 9, 6), "100", "0");
        Shift newerVea = shift(3L, "vea7", LocalDate.of(2026, 9, 8), "100", "0");
        ShiftFilter filter = ShiftFilter.of(null, null, null, null, "station", "asc");
        when(shiftRepository.findFiltered(OWNER_EMAIL, LocalDate.of(1, 1, 1), LocalDate.of(9999, 12, 31), "", "", ShiftStatus.HISTORY))
                .thenReturn(List.of(olderVea, bdl, newerVea));

        List<ShiftResponse> result = shiftService.getShifts(OWNER_EMAIL, filter);

        assertThat(result).extracting(ShiftResponse::getId).containsExactly(2L, 3L, 1L);
    }

    @Test
    void getStations_returnsCleanAlphabeticalValues() {
        when(shiftRepository.findDistinctStations(OWNER_EMAIL))
                .thenReturn(List.of("VEA7", " BDL4 ", "VEA6"));

        assertThat(shiftService.getStations(OWNER_EMAIL)).containsExactly("BDL4", "VEA6", "VEA7");
    }

    @Test
    void invalidDateRange_isRejectedBeforeAQueryRuns() {
        assertThatThrownBy(() -> ShiftFilter.of(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 1),
                null, null, null, null))
                .isInstanceOf(InvalidFilterException.class)
                .hasMessageContaining("start date");
    }

    @Test
    void updateShift_updatesAnOwnedShift() {
        Shift existing = shift(1L, "VEA7", LocalDate.of(2026, 9, 6), "121", "36.50");
        when(shiftRepository.findByIdAndOwnerEmailIgnoreCase(1L, OWNER_EMAIL)).thenReturn(Optional.of(existing));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShiftResponse result = shiftService.updateShift(OWNER_EMAIL, 1L, updateRequest());

        assertThat(result.getTotalPay()).isEqualByComparingTo("120.00");
        assertThat(existing.getOwner()).isSameAs(owner);
        verify(shiftRepository).save(existing);
    }

    @Test
    void updateShift_hidesARecordNotOwnedByTheSignedInUser() {
        when(shiftRepository.findByIdAndOwnerEmailIgnoreCase(999L, OWNER_EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shiftService.updateShift(OWNER_EMAIL, 999L, updateRequest()))
                .isInstanceOf(ShiftNotFoundException.class)
                .hasMessage("Shift not found with id: 999");
        verify(shiftRepository, never()).save(any());
    }

    @Test
    void deleteShift_softDeletesAnOwnedShiftAndReturnsItsUndoBatch() {
        when(shiftRepository.softDelete(eq(OWNER_EMAIL), eq(1L), eq(Instant.parse("2026-09-11T12:00:00Z")),
                any(String.class))).thenReturn(1);

        String batch = shiftService.deleteShift(OWNER_EMAIL, 1L);

        assertThat(batch).isNotBlank();
        verify(shiftRepository).softDelete(OWNER_EMAIL, 1L, Instant.parse("2026-09-11T12:00:00Z"), batch);
        verify(shiftRepository, never()).save(any());
    }

    @Test
    void deleteShift_rejectsAnIdThatMatchesNoLiveShiftForTheOwner() {
        when(shiftRepository.softDelete(eq(OWNER_EMAIL), eq(999L), any(Instant.class), any(String.class)))
                .thenReturn(0);

        assertThatThrownBy(() -> shiftService.deleteShift(OWNER_EMAIL, 999L))
                .isInstanceOf(ShiftNotFoundException.class)
                .hasMessage("Shift not found with id: 999");
    }

    @Test
    void getTrash_purgesOnlyTheCallersExpiredRows() {
        when(shiftRepository.findTrash(OWNER_EMAIL)).thenReturn(List.of());

        shiftService.getTrash(OWNER_EMAIL);

        verify(shiftRepository).purgeDeletedBefore(OWNER_EMAIL, Instant.parse("2026-08-12T12:00:00Z"));
    }

    private CreateShiftRequest request() {
        return new CreateShiftRequest("VEA7", LocalDate.of(2026, 9, 3), LocalTime.of(9, 0),
                LocalTime.of(17, 0), new BigDecimal("120.00"), new BigDecimal("35.50"));
    }

    private UpdateShiftRequest updateRequest() {
        return new UpdateShiftRequest("VEA7", LocalDate.of(2026, 9, 6), LocalTime.of(9, 0),
                LocalTime.of(17, 0), new BigDecimal("120.00"), BigDecimal.ZERO);
    }

    private Shift shift(Long id, String station, LocalDate date, String base, String tips) {
        return new Shift(id, station, date, LocalTime.of(9, 0), LocalTime.of(17, 0),
                new BigDecimal(base), new BigDecimal(tips), owner);
    }

    @Test
    void createShift_savesAScheduledBlockWithoutCountingItsOfferedPay() {
        when(userRepository.findByEmailIgnoreCase(OWNER_EMAIL)).thenReturn(Optional.of(owner));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShiftResponse result = shiftService.createShift(OWNER_EMAIL, new CreateShiftRequest("VEA7",
                LocalDate.of(2026, 9, 13), LocalTime.of(15, 15), LocalTime.of(19, 15), new BigDecimal("84.00"),
                BigDecimal.ZERO, null, ShiftStatus.SCHEDULED));

        assertThat(result.getStatus()).isEqualTo(ShiftStatus.SCHEDULED);
        assertThat(result.getEarnedPay()).isEqualByComparingTo("0.00");
        assertThat(result.getNetPay()).isEqualByComparingTo("0.00");
    }

    @Test
    void createShift_rejectsTipsOnAScheduledBlockWithAMessageNamingTheStatus() {
        when(userRepository.findByEmailIgnoreCase(OWNER_EMAIL)).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> shiftService.createShift(OWNER_EMAIL, new CreateShiftRequest("VEA7",
                LocalDate.of(2026, 9, 13), LocalTime.of(15, 15), LocalTime.of(19, 15), new BigDecimal("84.00"),
                new BigDecimal("5.00"), null, ShiftStatus.SCHEDULED)))
                .isInstanceOf(InvalidShiftException.class)
                .hasMessage("A scheduled shift cannot have tips yet.");
        verify(shiftRepository, never()).save(any());
    }

    @Test
    void changeStatus_completesAScheduledShiftInPlace() {
        Shift scheduled = scheduled(7L);
        when(shiftRepository.findByIdAndOwnerEmailIgnoreCase(7L, OWNER_EMAIL)).thenReturn(Optional.of(scheduled));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShiftResponse result = shiftService.changeStatus(OWNER_EMAIL, 7L, new ShiftStatusRequest(
                ShiftStatus.COMPLETED, new BigDecimal("86.50"), new BigDecimal("12.00"), new BigDecimal("31.5")));

        assertThat(result.getId()).isEqualTo(7L);
        assertThat(result.getStatus()).isEqualTo(ShiftStatus.COMPLETED);
        assertThat(result.getEarnedPay()).isEqualByComparingTo("98.50");
        assertThat(result.getMiles()).isEqualByComparingTo("31.5");
        assertThat(scheduled.getStatusChangedAt()).isEqualTo(Instant.parse("2026-09-11T12:00:00Z"));
    }

    @Test
    void changeStatus_cancelledWithoutPayDropsTheOfferedAmount() {
        Shift scheduled = scheduled(7L);
        when(shiftRepository.findByIdAndOwnerEmailIgnoreCase(7L, OWNER_EMAIL)).thenReturn(Optional.of(scheduled));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShiftResponse result = shiftService.changeStatus(OWNER_EMAIL, 7L, new ShiftStatusRequest(ShiftStatus.CANCELLED));

        assertThat(result.getBasePay()).isEqualByComparingTo("0");
        assertThat(result.getEarnedPay()).isEqualByComparingTo("0.00");
    }

    @Test
    void changeStatus_countsCancellationPayButNoHours() {
        Shift scheduled = scheduled(7L);
        when(shiftRepository.findByIdAndOwnerEmailIgnoreCase(7L, OWNER_EMAIL)).thenReturn(Optional.of(scheduled));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShiftResponse result = shiftService.changeStatus(OWNER_EMAIL, 7L,
                new ShiftStatusRequest(ShiftStatus.CANCELLED, new BigDecimal("18.00"), null, null));

        assertThat(result.getEarnedPay()).isEqualByComparingTo("18.00");
        assertThat(result.getNetPay()).isEqualByComparingTo("18.00");
        assertThat(result.getNetHourlyRate()).isEqualByComparingTo("0.00");
    }

    @Test
    void changeStatus_refusesToMoveAWorkedShiftBackToScheduled() {
        Shift completed = shift(1L, "VEA7", LocalDate.of(2026, 9, 6), "120", "10");
        when(shiftRepository.findByIdAndOwnerEmailIgnoreCase(1L, OWNER_EMAIL)).thenReturn(Optional.of(completed));

        assertThatThrownBy(() -> shiftService.changeStatus(OWNER_EMAIL, 1L, new ShiftStatusRequest(ShiftStatus.SCHEDULED)))
                .isInstanceOf(InvalidShiftException.class)
                .hasMessage("A completed shift cannot be moved back to scheduled. Delete it and add the block again.");
        verify(shiftRepository, never()).save(any());
    }

    @Test
    void updateShift_keepsTheCurrentStatusWhenTheRequestOmitsIt() {
        Shift scheduled = scheduled(7L);
        when(shiftRepository.findByIdAndOwnerEmailIgnoreCase(7L, OWNER_EMAIL)).thenReturn(Optional.of(scheduled));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShiftResponse result = shiftService.updateShift(OWNER_EMAIL, 7L, updateRequest());

        assertThat(result.getStatus()).isEqualTo(ShiftStatus.SCHEDULED);
        assertThat(scheduled.getStatusChangedAt()).isNull();
    }

    private Shift scheduled(Long id) {
        Shift shift = new Shift(id, "VEA7", LocalDate.of(2026, 9, 13), LocalTime.of(15, 15), LocalTime.of(19, 15),
                new BigDecimal("84.00"), BigDecimal.ZERO, owner);
        shift.setStatus(ShiftStatus.SCHEDULED);
        return shift;
    }

    @Test
    void startShift_stampsTheCurrentMinuteInTheDriversZone() {
        Shift scheduled = scheduled(7L);
        when(shiftRepository.findByIdAndOwnerEmailIgnoreCase(7L, OWNER_EMAIL)).thenReturn(Optional.of(scheduled));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userTime.now(OWNER_EMAIL)).thenReturn(LocalDateTime.of(2026, 9, 13, 15, 3, 40));

        ShiftResponse result = shiftService.startShift(OWNER_EMAIL, 7L);

        assertThat(result.getStatus()).isEqualTo(ShiftStatus.SCHEDULED);
        assertThat(result.getDetails().actualStart()).isEqualTo(LocalTime.of(15, 3));
        assertThat(result.getDetails().actualEnd()).isNull();
    }

    @Test
    void startShift_opensTwoHoursBeforeTheBlockAndClosesAtItsEnd() {
        when(shiftRepository.findByIdAndOwnerEmailIgnoreCase(7L, OWNER_EMAIL)).thenReturn(Optional.of(scheduled(7L)));

        when(userTime.now(OWNER_EMAIL)).thenReturn(LocalDateTime.of(2026, 9, 13, 13, 14));
        assertThatThrownBy(() -> shiftService.startShift(OWNER_EMAIL, 7L))
                .isInstanceOf(InvalidShiftException.class)
                .hasMessage("A block can be started from 2 hours before its scheduled start.");
        when(userTime.now(OWNER_EMAIL)).thenReturn(LocalDateTime.of(2026, 9, 13, 19, 16));
        assertThatThrownBy(() -> shiftService.startShift(OWNER_EMAIL, 7L))
                .isInstanceOf(InvalidShiftException.class)
                .hasMessage("This block's scheduled time has passed. Mark it completed instead.");
        verify(shiftRepository, never()).save(any());
    }

    @Test
    void startShift_refusesABlockThatIsNoLongerScheduled() {
        when(shiftRepository.findByIdAndOwnerEmailIgnoreCase(1L, OWNER_EMAIL))
                .thenReturn(Optional.of(shift(1L, "VEA7", LocalDate.of(2026, 9, 6), "120", "10")));

        assertThatThrownBy(() -> shiftService.startShift(OWNER_EMAIL, 1L))
                .isInstanceOf(InvalidShiftException.class)
                .hasMessage("Only a scheduled block can be started.");
    }

    @Test
    void changeStatus_completesWithActualTimesAndReportsTheWorkedRate() {
        Shift scheduled = scheduled(7L);
        when(shiftRepository.findByIdAndOwnerEmailIgnoreCase(7L, OWNER_EMAIL)).thenReturn(Optional.of(scheduled));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShiftResponse result = shiftService.changeStatus(OWNER_EMAIL, 7L, new ShiftStatusRequest(ShiftStatus.COMPLETED,
                new BigDecimal("84.00"), null, null, times(LocalTime.of(15, 20), LocalTime.of(18, 42))));

        // Paid for the scheduled 4 hours, on the clock for 3 h 22 m: the worked rate is 19 percent higher.
        assertThat(result.getTimeWorked()).isEqualTo(240);
        assertThat(result.getHourlyRate()).isEqualByComparingTo("21.00");
        assertThat(result.getDetails().actualMinutes()).isEqualTo(202);
        assertThat(result.getDetails().finishedEarlyMinutes()).isEqualTo(38);
        assertThat(result.getDetails().actualHourlyRate()).isEqualByComparingTo("24.95");
    }

    @Test
    void actualTimesPastMidnightFinishTheNextDay() {
        Shift scheduled = scheduled(7L);
        when(shiftRepository.findByIdAndOwnerEmailIgnoreCase(7L, OWNER_EMAIL)).thenReturn(Optional.of(scheduled));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShiftResponse result = shiftService.changeStatus(OWNER_EMAIL, 7L, new ShiftStatusRequest(ShiftStatus.COMPLETED,
                null, null, null, times(LocalTime.of(23, 30), LocalTime.of(1, 10))));

        assertThat(result.getDetails().actualMinutes()).isEqualTo(100);
        assertThat(result.getDetails().finishedEarlyMinutes()).isEqualTo(140);
    }

    @Test
    void updateShift_keepsSavedActualTimesUnlessTheRequestCarriesDetails() {
        Shift completed = shift(1L, "VEA7", LocalDate.of(2026, 9, 6), "120", "0");
        completed.setActualStart(LocalTime.of(9, 5));
        completed.setActualEnd(LocalTime.of(16, 10));
        when(shiftRepository.findByIdAndOwnerEmailIgnoreCase(1L, OWNER_EMAIL)).thenReturn(Optional.of(completed));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(invocation -> invocation.getArgument(0));

        shiftService.updateShift(OWNER_EMAIL, 1L, updateRequest());
        assertThat(completed.getActualEnd()).isEqualTo(LocalTime.of(16, 10));

        UpdateShiftRequest cleared = updateRequest();
        cleared.setDetails(times(null, null));
        shiftService.updateShift(OWNER_EMAIL, 1L, cleared);
        assertThat(completed.getActualStart()).isNull();
        assertThat(completed.getActualEnd()).isNull();
    }

    @Test
    void cancellingAWorkedBlockDropsItsActualTimes() {
        Shift completed = shift(1L, "VEA7", LocalDate.of(2026, 9, 6), "120", "0");
        completed.setActualStart(LocalTime.of(9, 5));
        completed.setActualEnd(LocalTime.of(16, 10));
        when(shiftRepository.findByIdAndOwnerEmailIgnoreCase(1L, OWNER_EMAIL)).thenReturn(Optional.of(completed));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(invocation -> invocation.getArgument(0));

        shiftService.changeStatus(OWNER_EMAIL, 1L, new ShiftStatusRequest(ShiftStatus.CANCELLED));

        assertThat(completed.getActualStart()).isNull();
        assertThat(completed.getActualEnd()).isNull();
    }

    @Test
    void actualTimesMustMakeSenseForTheBlock() {
        Shift completed = shift(1L, "VEA7", LocalDate.of(2026, 9, 6), "120", "0");
        when(shiftRepository.findByIdAndOwnerEmailIgnoreCase(1L, OWNER_EMAIL)).thenReturn(Optional.of(completed));
        Shift scheduled = scheduled(7L);
        when(shiftRepository.findByIdAndOwnerEmailIgnoreCase(7L, OWNER_EMAIL)).thenReturn(Optional.of(scheduled));

        assertDetailsRejected(1L, times(null, LocalTime.of(16, 0)), "Enter when the block started.");
        assertDetailsRejected(1L, times(LocalTime.of(9, 0), LocalTime.of(9, 0)), "The finish time must be after the start time.");
        assertDetailsRejected(7L, times(LocalTime.of(15, 0), LocalTime.of(18, 0)),
                "Mark the block completed to record when it finished.");
        assertThatThrownBy(() -> shiftService.changeStatus(OWNER_EMAIL, 1L, new ShiftStatusRequest(ShiftStatus.FORFEITED,
                null, null, null, times(LocalTime.of(9, 0), LocalTime.of(12, 0)))))
                .isInstanceOf(InvalidShiftException.class)
                .hasMessage("Actual times are only recorded for completed blocks.");
        verify(shiftRepository, never()).save(any());
    }

    private void assertDetailsRejected(Long id, BlockDetailsRequest details, String message) {
        UpdateShiftRequest request = updateRequest();
        request.setDetails(details);
        assertThatThrownBy(() -> shiftService.updateShift(OWNER_EMAIL, id, request))
                .isInstanceOf(InvalidShiftException.class)
                .hasMessage(message);
    }

    private static BlockDetailsRequest times(LocalTime start, LocalTime end) {
        return new BlockDetailsRequest(start, end, null, null, null, null, null);
    }

    @Test
    void odometerReadingsFillInMilesWhenNoneAreTyped() {
        Shift scheduled = scheduled(7L);
        when(shiftRepository.findByIdAndOwnerEmailIgnoreCase(7L, OWNER_EMAIL)).thenReturn(Optional.of(scheduled));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShiftResponse result = shiftService.changeStatus(OWNER_EMAIL, 7L, new ShiftStatusRequest(ShiftStatus.COMPLETED,
                null, null, null, odometer("45210.4", "45233.8")));

        assertThat(result.getMiles()).isEqualByComparingTo("23.4");
        assertThat(result.getDetails().odometerStart()).isEqualByComparingTo("45210.4");
        assertThat(result.getDetails().odometerEnd()).isEqualByComparingTo("45233.8");
    }

    @Test
    void typedMilesWinOverTheOdometerAndKeepTheReadings() {
        Shift completed = shift(1L, "VEA7", LocalDate.of(2026, 9, 6), "120", "0");
        when(shiftRepository.findByIdAndOwnerEmailIgnoreCase(1L, OWNER_EMAIL)).thenReturn(Optional.of(completed));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(invocation -> invocation.getArgument(0));
        UpdateShiftRequest request = new UpdateShiftRequest("VEA7", LocalDate.of(2026, 9, 6), LocalTime.of(9, 0),
                LocalTime.of(17, 0), new BigDecimal("120.00"), BigDecimal.ZERO, new BigDecimal("20.0"));
        request.setDetails(odometer("45210.4", "45233.8"));

        ShiftResponse result = shiftService.updateShift(OWNER_EMAIL, 1L, request);

        assertThat(result.getMiles()).isEqualByComparingTo("20.0");
        assertThat(completed.getOdometerStart()).isEqualByComparingTo("45210.4");
        assertThat(completed.getOdometerEnd()).isEqualByComparingTo("45233.8");
    }

    @Test
    void odometerReadingsMustNotRunBackwardsOrBeRecordedBeforeTheBlock() {
        when(shiftRepository.findByIdAndOwnerEmailIgnoreCase(1L, OWNER_EMAIL))
                .thenReturn(Optional.of(shift(1L, "VEA7", LocalDate.of(2026, 9, 6), "120", "0")));
        when(shiftRepository.findByIdAndOwnerEmailIgnoreCase(7L, OWNER_EMAIL)).thenReturn(Optional.of(scheduled(7L)));

        assertDetailsRejected(1L, odometer("45233.8", "45210.4"),
                "The odometer end reading must be at least the start reading.");
        assertDetailsRejected(7L, odometer("45210.4", null), "Record the odometer when the block is finished.");
        verify(shiftRepository, never()).save(any());
    }

    @Test
    void latestOdometerLooksBeforeTheGivenMomentOrNow() {
        Shift previous = shift(3L, "VEA7", LocalDate.of(2026, 9, 6), "120", "0");
        previous.setOdometerEnd(new BigDecimal("45210.4"));
        when(shiftRepository.findWithOdometerBefore(eq(OWNER_EMAIL), eq(LocalDate.of(2026, 9, 13)),
                eq(LocalTime.of(15, 15)), any())).thenReturn(List.of(previous));
        when(userTime.now(OWNER_EMAIL)).thenReturn(LocalDateTime.of(2026, 9, 13, 15, 15));

        assertThat(shiftService.latestOdometer(OWNER_EMAIL, null)).get()
                .satisfies(latest -> assertThat(latest.reading()).isEqualByComparingTo("45210.4"))
                .satisfies(latest -> assertThat(latest.station()).isEqualTo("VEA7"));
        assertThat(shiftService.latestOdometer(OWNER_EMAIL, LocalDateTime.of(2026, 9, 1, 8, 0))).isEmpty();
    }

    private static BlockDetailsRequest odometer(String start, String end) {
        return new BlockDetailsRequest(null, null, start == null ? null : new BigDecimal(start),
                end == null ? null : new BigDecimal(end), null, null, null);
    }

    @Test
    void routeCountsGiveMinutesPerStopAndAReturnsRate() {
        Shift scheduled = scheduled(7L);
        when(shiftRepository.findByIdAndOwnerEmailIgnoreCase(7L, OWNER_EMAIL)).thenReturn(Optional.of(scheduled));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShiftResponse paid = shiftService.changeStatus(OWNER_EMAIL, 7L, new ShiftStatusRequest(ShiftStatus.COMPLETED,
                null, null, null, route(null, null, 42, 60, 3)));
        assertThat(paid.getDetails().minutesPerStop()).isEqualByComparingTo("5.7");
        assertThat(paid.getDetails().returnsRate()).isEqualByComparingTo("5.0");

        ShiftResponse timed = shiftService.changeStatus(OWNER_EMAIL, 7L, new ShiftStatusRequest(ShiftStatus.COMPLETED,
                null, null, null, route(LocalTime.of(15, 20), LocalTime.of(18, 42), 42, null, 1)));
        assertThat(timed.getDetails().minutesPerStop()).isEqualByComparingTo("4.8");
        assertThat(timed.getDetails().returnsRate()).isEqualByComparingTo("2.4");
    }

    @Test
    void routeCountsNeedACompletedBlockAndNoMoreReturnsThanPackages() {
        when(shiftRepository.findByIdAndOwnerEmailIgnoreCase(1L, OWNER_EMAIL))
                .thenReturn(Optional.of(shift(1L, "VEA7", LocalDate.of(2026, 9, 6), "120", "0")));

        assertDetailsRejected(1L, route(null, null, 40, 50, 51), "Returns cannot be more than the packages carried.");
        assertThatThrownBy(() -> shiftService.changeStatus(OWNER_EMAIL, 1L, new ShiftStatusRequest(ShiftStatus.CANCELLED,
                null, null, null, route(null, null, 40, null, null))))
                .isInstanceOf(InvalidShiftException.class)
                .hasMessage("Stops, packages, and returns are only recorded for completed blocks.");
        verify(shiftRepository, never()).save(any());
    }

    @Test
    void cancellingAWorkedBlockDropsItsRouteCountsButKeepsItsOdometer() {
        Shift completed = shift(1L, "VEA7", LocalDate.of(2026, 9, 6), "120", "0");
        completed.setStops(42);
        completed.setOdometerStart(new BigDecimal("45210.4"));
        completed.setOdometerEnd(new BigDecimal("45233.8"));
        when(shiftRepository.findByIdAndOwnerEmailIgnoreCase(1L, OWNER_EMAIL)).thenReturn(Optional.of(completed));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(invocation -> invocation.getArgument(0));

        shiftService.changeStatus(OWNER_EMAIL, 1L, new ShiftStatusRequest(ShiftStatus.CANCELLED));

        assertThat(completed.getStops()).isNull();
        assertThat(completed.getOdometerEnd()).isEqualByComparingTo("45233.8");
        assertThat(completed.getMiles()).isEqualByComparingTo("23.4");
    }

    @Test
    void sortingByMinutesPerStopPutsBlocksWithoutStopsLastEitherWay() {
        Shift slow = shift(1L, "VEA7", LocalDate.of(2026, 9, 6), "120", "0");
        slow.setStops(40);
        Shift quick = shift(2L, "VEA7", LocalDate.of(2026, 9, 7), "120", "0");
        quick.setStops(96);
        Shift uncounted = shift(3L, "VEA7", LocalDate.of(2026, 9, 8), "120", "0");
        when(shiftRepository.findFiltered(any(), any(), any(), any(), any(), any()))
                .thenReturn(List.of(slow, quick, uncounted));

        assertThat(shiftService.getShifts(OWNER_EMAIL, ShiftFilter.of(null, null, null, null, "minutesPerStop", "asc")))
                .extracting(ShiftResponse::getId).containsExactly(2L, 1L, 3L);
        assertThat(shiftService.getShifts(OWNER_EMAIL, ShiftFilter.of(null, null, null, null, "minutesPerStop", "desc")))
                .extracting(ShiftResponse::getId).containsExactly(1L, 2L, 3L);
    }

    @Test
    void aForfeitIsLateOnlyAfterTheCutoffBeforeTheStart() {
        // The block starts at 15:15; with the default 45-minute cutoff the deadline is 14:30.
        assertThat(forfeitAt(LocalDateTime.of(2026, 9, 13, 14, 20))).isFalse();
        assertThat(forfeitAt(LocalDateTime.of(2026, 9, 13, 14, 29))).isFalse();
        assertThat(forfeitAt(LocalDateTime.of(2026, 9, 13, 14, 30))).isFalse();
        assertThat(forfeitAt(LocalDateTime.of(2026, 9, 13, 14, 31))).isTrue();
        assertThat(forfeitAt(LocalDateTime.of(2026, 9, 13, 14, 40))).isTrue();
    }

    @Test
    void aForfeitKeepsItsJudgementWhenTheCutoffChangesLater() {
        Shift scheduled = scheduled(7L);
        when(shiftRepository.findByIdAndOwnerEmailIgnoreCase(7L, OWNER_EMAIL)).thenReturn(Optional.of(scheduled));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userTime.now(OWNER_EMAIL)).thenReturn(LocalDateTime.of(2026, 9, 13, 14, 20));
        shiftService.changeStatus(OWNER_EMAIL, 7L, new ShiftStatusRequest(ShiftStatus.FORFEITED));
        assertThat(scheduled.isLateForfeit()).isFalse();

        when(settingsService.get(OWNER_EMAIL)).thenReturn(new com.angel.flexbuddy.dto.AccountSettingsResponse(
                com.angel.flexbuddy.model.VehicleCostMethod.STANDARD_MILEAGE, new BigDecimal("0.70"),
                new BigDecimal("0.70"), 2025, "America/New_York", null, false, false, 60, null));
        ShiftResponse edited = shiftService.changeStatus(OWNER_EMAIL, 7L, new ShiftStatusRequest(ShiftStatus.FORFEITED));

        assertThat(edited.isLateForfeit()).isFalse();
    }

    @Test
    void completingAForfeitedBlockClearsTheLateFlag() {
        Shift forfeited = scheduled(7L);
        forfeited.setStatus(ShiftStatus.FORFEITED);
        forfeited.setBasePay(BigDecimal.ZERO);
        forfeited.setLateForfeit(true);
        when(shiftRepository.findByIdAndOwnerEmailIgnoreCase(7L, OWNER_EMAIL)).thenReturn(Optional.of(forfeited));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(invocation -> invocation.getArgument(0));

        shiftService.changeStatus(OWNER_EMAIL, 7L, new ShiftStatusRequest(ShiftStatus.COMPLETED,
                new BigDecimal("84.00"), null, null));

        assertThat(forfeited.isLateForfeit()).isFalse();
    }

    @Test
    void aScheduledBlockReportsItsForfeitDeadline() {
        Shift scheduled = scheduled(7L);
        when(shiftRepository.findByIdAndOwnerEmailIgnoreCase(7L, OWNER_EMAIL)).thenReturn(Optional.of(scheduled));

        ShiftResponse response = shiftService.getShift(OWNER_EMAIL, 7L);

        assertThat(response.getForfeitDeadline()).isEqualTo(LocalDateTime.of(2026, 9, 13, 14, 30));
        assertThat(response.isLateForfeit()).isFalse();
    }

    private boolean forfeitAt(LocalDateTime now) {
        Shift scheduled = scheduled(7L);
        when(shiftRepository.findByIdAndOwnerEmailIgnoreCase(7L, OWNER_EMAIL)).thenReturn(Optional.of(scheduled));
        org.mockito.Mockito.lenient().when(shiftRepository.save(any(Shift.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(userTime.now(OWNER_EMAIL)).thenReturn(now);
        ShiftResponse response = shiftService.changeStatus(OWNER_EMAIL, 7L, new ShiftStatusRequest(ShiftStatus.FORFEITED));
        assertThat(response.getForfeitDeadline()).isNull();
        return response.isLateForfeit();
    }

    private static BlockDetailsRequest route(LocalTime start, LocalTime end, Integer stops, Integer packages,
            Integer returns) {
        return new BlockDetailsRequest(start, end, null, null, stops, packages, returns);
    }

    @Test
    void missingMilesListsRecentCompletedBlocksWithoutMilesNewestFirstUpToTen() {
        when(userTime.today(OWNER_EMAIL)).thenReturn(LocalDate.of(2026, 9, 13));
        List<Shift> recent = new java.util.ArrayList<>();
        for (int day = 0; day < 12; day++) {
            recent.add(shift(100L + day, "VEA7", LocalDate.of(2026, 9, 13).minusDays(day % 7), "120", "0"));
        }
        Shift logged = shift(1L, "VEA7", LocalDate.of(2026, 9, 13), "120", "0");
        logged.setMiles(BigDecimal.ZERO);
        recent.add(0, logged);
        when(shiftRepository.findFiltered(OWNER_EMAIL, LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 13), "", "",
                java.util.Set.of(ShiftStatus.COMPLETED))).thenReturn(recent);

        List<ShiftResponse> missing = shiftService.missingMiles(OWNER_EMAIL, 7);

        assertThat(missing).hasSize(10).extracting(ShiftResponse::getId).doesNotContain(1L).startsWith(100L, 101L);
        assertThatThrownBy(() -> shiftService.missingMiles(OWNER_EMAIL, 0))
                .isInstanceOf(com.angel.flexbuddy.exception.InvalidFilterException.class);
    }
}
