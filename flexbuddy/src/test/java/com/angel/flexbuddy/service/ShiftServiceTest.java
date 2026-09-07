package com.angel.flexbuddy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.angel.flexbuddy.dto.CreateShiftRequest;
import com.angel.flexbuddy.dto.ShiftResponse;
import com.angel.flexbuddy.dto.ShiftStatisticsResponse;
import com.angel.flexbuddy.dto.UpdateShiftRequest;
import com.angel.flexbuddy.exception.ShiftNotFoundException;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.ShiftRepository;

@ExtendWith(MockitoExtension.class)
class ShiftServiceTest {

    private static final String OWNER_EMAIL = "angel@example.com";

    @Mock
    private ShiftRepository shiftRepository;

    @Mock
    private AppUserRepository userRepository;

    @InjectMocks
    private ShiftService shiftService;

    private AppUser owner;

    @BeforeEach
    void setUpOwner() {
        owner = new AppUser("Angel", OWNER_EMAIL, "password-hash");
        owner.setId(10L);
    }

    @Test
    void createShift_assignsTheSignedInOwnerAndMapsFields() {
        CreateShiftRequest request = request();
        when(userRepository.findByEmailIgnoreCase(OWNER_EMAIL)).thenReturn(Optional.of(owner));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShiftResponse result = shiftService.createShift(OWNER_EMAIL, request);

        ArgumentCaptor<Shift> captor = ArgumentCaptor.forClass(Shift.class);
        verify(shiftRepository).save(captor.capture());
        Shift saved = captor.getValue();
        assertThat(saved.getOwner()).isSameAs(owner);
        assertThat(saved.getStation()).isEqualTo("VEA7");
        assertThat(saved.getBasePay()).isEqualByComparingTo("120.00");
        assertThat(saved.getTips()).isEqualByComparingTo("35.50");
        assertThat(result.getTotalPay()).isEqualByComparingTo("155.50");
    }

    @Test
    void getAllShifts_onlyQueriesTheSignedInOwnersShifts() {
        Shift shift = shift(1L, owner, "120.00", "35.50");
        when(shiftRepository.findAllByOwnerEmailIgnoreCaseOrderByDateDescStartTimeDesc(OWNER_EMAIL))
                .thenReturn(List.of(shift));

        List<ShiftResponse> result = shiftService.getAllShifts(OWNER_EMAIL);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().getId()).isEqualTo(1L);
        verify(shiftRepository).findAllByOwnerEmailIgnoreCaseOrderByDateDescStartTimeDesc(OWNER_EMAIL);
        verify(shiftRepository, never()).findAll();
    }

    @Test
    void getShiftStatistics_calculatesOnlyTheSignedInOwnersStatistics() {
        Shift firstShift = shift(1L, owner, "120.00", "35.50");
        Shift secondShift = new Shift(
                2L,
                "VEA6",
                LocalDate.of(2026, 9, 6),
                LocalTime.of(10, 15),
                LocalTime.of(14, 45),
                new BigDecimal("80.00"),
                new BigDecimal("10.00"),
                owner
        );
        when(shiftRepository.findAllByOwnerEmailIgnoreCaseOrderByDateDescStartTimeDesc(OWNER_EMAIL))
                .thenReturn(List.of(firstShift, secondShift));

        ShiftStatisticsResponse result = shiftService.getShiftStatistics(OWNER_EMAIL);

        assertThat(result.getTotalShifts()).isEqualTo(2);
        assertThat(result.getTotalBasePay()).isEqualByComparingTo("200.00");
        assertThat(result.getTotalTips()).isEqualByComparingTo("45.50");
        assertThat(result.getTotalEarnings()).isEqualByComparingTo("245.50");
        assertThat(result.getAveragePayPerShift()).isEqualByComparingTo("122.75");
        assertThat(result.getTotalTimeWorked()).isEqualTo(750);
    }

    @Test
    void getShiftStatistics_returnsZerosWhenOwnerHasNoShifts() {
        when(shiftRepository.findAllByOwnerEmailIgnoreCaseOrderByDateDescStartTimeDesc(OWNER_EMAIL))
                .thenReturn(List.of());

        ShiftStatisticsResponse result = shiftService.getShiftStatistics(OWNER_EMAIL);

        assertThat(result.getTotalShifts()).isZero();
        assertThat(result.getTotalEarnings()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getAveragePayPerShift()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getTotalTimeWorked()).isZero();
    }

    @Test
    void updateShift_updatesAnOwnedShift() {
        Long id = 1L;
        Shift existingShift = shift(id, owner, "121.00", "36.50");
        UpdateShiftRequest request = new UpdateShiftRequest(
                "VEA7",
                LocalDate.of(2026, 9, 7),
                LocalTime.of(10, 0),
                LocalTime.of(18, 0),
                new BigDecimal("130.00"),
                new BigDecimal("40.00")
        );
        when(shiftRepository.findByIdAndOwnerEmailIgnoreCase(id, OWNER_EMAIL))
                .thenReturn(Optional.of(existingShift));
        when(shiftRepository.save(any(Shift.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShiftResponse result = shiftService.updateShift(OWNER_EMAIL, id, request);

        assertThat(result.getStation()).isEqualTo("VEA7");
        assertThat(result.getTotalPay()).isEqualByComparingTo("170.00");
        assertThat(existingShift.getOwner()).isSameAs(owner);
        verify(shiftRepository).save(existingShift);
    }

    @Test
    void updateShift_hidesARecordNotOwnedByTheSignedInUser() {
        Long id = 999L;
        when(shiftRepository.findByIdAndOwnerEmailIgnoreCase(id, OWNER_EMAIL))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> shiftService.updateShift(OWNER_EMAIL, id, updateRequest()))
                .isInstanceOf(ShiftNotFoundException.class)
                .hasMessage("Shift not found with id: 999");

        verify(shiftRepository, never()).save(any(Shift.class));
    }

    @Test
    void deleteShift_deletesAnOwnedShift() {
        Shift existingShift = shift(1L, owner, "120.00", "35.50");
        when(shiftRepository.findByIdAndOwnerEmailIgnoreCase(1L, OWNER_EMAIL))
                .thenReturn(Optional.of(existingShift));

        shiftService.deleteShift(OWNER_EMAIL, 1L);

        verify(shiftRepository).delete(existingShift);
    }

    @Test
    void deleteShift_hidesARecordNotOwnedByTheSignedInUser() {
        when(shiftRepository.findByIdAndOwnerEmailIgnoreCase(999L, OWNER_EMAIL))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> shiftService.deleteShift(OWNER_EMAIL, 999L))
                .isInstanceOf(ShiftNotFoundException.class)
                .hasMessage("Shift not found with id: 999");

        verify(shiftRepository, never()).delete(any(Shift.class));
    }

    private CreateShiftRequest request() {
        return new CreateShiftRequest(
                "VEA7",
                LocalDate.of(2026, 9, 3),
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                new BigDecimal("120.00"),
                new BigDecimal("35.50")
        );
    }

    private UpdateShiftRequest updateRequest() {
        return new UpdateShiftRequest(
                "VEA7",
                LocalDate.of(2026, 9, 6),
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                new BigDecimal("120.00"),
                BigDecimal.ZERO
        );
    }

    private Shift shift(Long id, AppUser shiftOwner, String basePay, String tips) {
        return new Shift(
                id,
                "VEA7",
                LocalDate.of(2026, 9, 6),
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                new BigDecimal(basePay),
                new BigDecimal(tips),
                shiftOwner
        );
    }
}
