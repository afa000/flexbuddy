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

import com.angel.flexbuddy.dto.CreateShiftRequest;
import com.angel.flexbuddy.dto.ShiftFilter;
import com.angel.flexbuddy.dto.ShiftResponse;
import com.angel.flexbuddy.dto.UpdateShiftRequest;
import com.angel.flexbuddy.exception.InvalidFilterException;
import com.angel.flexbuddy.exception.ShiftNotFoundException;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.Shift;
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
        when(shiftRepository.findFiltered(OWNER_EMAIL, LocalDate.of(1, 1, 1), LocalDate.of(9999, 12, 31), "", ""))
                .thenReturn(List.of(shift(1L, "VEA7", LocalDate.of(2026, 9, 6), "120", "10")));

        List<ShiftResponse> result = shiftService.getAllShifts(OWNER_EMAIL);

        assertThat(result).extracting(ShiftResponse::getId).containsExactly(1L);
        verify(shiftRepository).findFiltered(OWNER_EMAIL, LocalDate.of(1, 1, 1), LocalDate.of(9999, 12, 31), "", "");
        verify(shiftRepository, never()).findAll();
    }

    @Test
    void getShifts_passesNormalizedFiltersToRepositoryAndSortsHourlyRateDescending() {
        Shift low = shift(1L, "VEA7", LocalDate.of(2026, 9, 8), "80", "0");
        Shift high = shift(2L, "VEA6", LocalDate.of(2026, 9, 7), "160", "0");
        ShiftFilter filter = ShiftFilter.of(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                " VEA7 ", " vea ", "hourlyRate", "desc");
        when(shiftRepository.findFiltered(OWNER_EMAIL, filter.from(), filter.to(), "VEA7", "vea"))
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
        when(shiftRepository.findFiltered(OWNER_EMAIL, LocalDate.of(1, 1, 1), LocalDate.of(9999, 12, 31), "", ""))
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
}
