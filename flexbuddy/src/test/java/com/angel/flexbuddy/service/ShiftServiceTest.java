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
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.repository.ShiftRepository;

@ExtendWith(MockitoExtension.class)
class ShiftServiceTest {

    @Mock
    private ShiftRepository shiftRepository;

    @InjectMocks
    private ShiftService shiftService;

    @Test
    void createShift_mapsAllRequestFieldsOntoSavedShift() {

        CreateShiftRequest request = new CreateShiftRequest(
                "VEA7",
                LocalDate.of(2026, 9, 3),
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                new BigDecimal("120.00"),
                new BigDecimal("35.50"));

        when(shiftRepository.save(any(Shift.class))).thenAnswer(inv -> inv.getArgument(0));

        ShiftResponse result = shiftService.createShift(request);

        ArgumentCaptor<Shift> captor = ArgumentCaptor.forClass(Shift.class);
        verify(shiftRepository).save(captor.capture());
        Shift saved = captor.getValue();

        assertThat(saved.getStation()).isEqualTo("VEA7");
        assertThat(saved.getDate()).isEqualTo(LocalDate.of(2026, 9, 3));
        assertThat(saved.getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(saved.getEndTime()).isEqualTo(LocalTime.of(17, 0));
        assertThat(saved.getBasePay()).isEqualByComparingTo("120.00");
        assertThat(saved.getTips()).isEqualByComparingTo("35.50");

        assertThat(result.getTotalPay()).isEqualByComparingTo("155.50");
    }

    @Test
    void getShiftStatistics_calculatesStatisticsAcrossAllShifts(){

        Shift firstShift = new Shift(
            1L,
            "VEA7",
            LocalDate.of(2026, 9, 6),
            LocalTime.of(9, 0),
            LocalTime.of(17,0),
            new BigDecimal("120.00"),
            new BigDecimal("35.50")
        );

        Shift secondShift = new Shift(
            2L,
            "VEA6",
            LocalDate.of(2026, 9, 6),
            LocalTime.of(10, 15),
            LocalTime.of(14, 45),
            new BigDecimal("80.00"),
            new BigDecimal("10.00")
        );

        when(shiftRepository.findAll()).thenReturn(List.of(firstShift, secondShift));

        ShiftStatisticsResponse result = shiftService.getShiftStatistics();

        assertThat(result.getTotalShifts()).isEqualTo(2);
        assertThat(result.getTotalBasePay())
                .isEqualByComparingTo("200.00");
        assertThat(result.getTotalTips())
                .isEqualByComparingTo("45.50");
        assertThat(result.getTotalEarnings())
                .isEqualByComparingTo("245.50");
        assertThat(result.getAveragePayPerShift())
                .isEqualByComparingTo("122.75");
        assertThat(result.getTotalTimeWorked()).isEqualTo(750);

        verify(shiftRepository).findAll();
    }

    @Test
    void getShiftStatistics_returnsZerosWhenNoShiftsExist() {

        when(shiftRepository.findAll()).thenReturn(List.of());

        ShiftStatisticsResponse result =
                shiftService.getShiftStatistics();

        assertThat(result.getTotalShifts()).isZero();
        assertThat(result.getTotalBasePay())
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getTotalTips())
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getTotalEarnings())
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getAveragePayPerShift())
                .isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getTotalTimeWorked()).isZero();
    }

    @Test 
    void updateShift_updatesAndReturnsExistingShift() {

        Long id = 1L;

        Shift existingShift = new Shift(
            1L,
            "VEA6",
            LocalDate.of(2026, 9, 4),
            LocalTime.of(8, 0),
            LocalTime.of(16,0),
            new BigDecimal("121.00"),
            new BigDecimal("36.50")
        );

        UpdateShiftRequest request = new UpdateShiftRequest(
            "VEA7",
            LocalDate.of(2026, 9, 6),
            LocalTime.of(9, 0),
            LocalTime.of(17,0),
            new BigDecimal("120.00"),
            new BigDecimal("35.50")
        );

        when(shiftRepository.findById(id)).thenReturn(Optional.of(existingShift));

        when(shiftRepository.save(any(Shift.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ShiftResponse result = shiftService.updateShift(id, request);
        
        ArgumentCaptor<Shift> captor = ArgumentCaptor.forClass(Shift.class);

        verify(shiftRepository).findById(id);
        verify(shiftRepository).save(captor.capture());

        Shift savedShift = captor.getValue();

        assertThat(savedShift.getId()).isEqualTo(id);
        assertThat(savedShift.getStation()).isEqualTo("VEA7");
        assertThat(savedShift.getDate())
                .isEqualTo(LocalDate.of(2026, 9, 6));
        assertThat(savedShift.getStartTime())
                .isEqualTo(LocalTime.of(9, 0));
        assertThat(savedShift.getEndTime())
                .isEqualTo(LocalTime.of(17, 0));
        assertThat(savedShift.getBasePay())
                .isEqualByComparingTo("120.00");
        assertThat(savedShift.getTips())
                .isEqualByComparingTo("35.50");

        assertThat(result.getId()).isEqualTo(id);
        assertThat(result.getStation()).isEqualTo("VEA7");
        assertThat(result.getDate())
                .isEqualTo(LocalDate.of(2026, 9, 6));
        assertThat(result.getStartTime())
                .isEqualTo(LocalTime.of(9, 0));
        assertThat(result.getEndTime())
                .isEqualTo(LocalTime.of(17, 0));
        assertThat(result.getBasePay())
                .isEqualByComparingTo("120.00");
        assertThat(result.getTips())
                .isEqualByComparingTo("35.50");
        assertThat(result.getTotalPay())
                .isEqualByComparingTo("155.50");
    }

    @Test
    void updateShift_throwsWhenShiftDoesNotExist() {
        
        Long id = 999L;
        UpdateShiftRequest request = new UpdateShiftRequest(
                "VEA7",
                LocalDate.of(2026, 9, 6),
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                new BigDecimal("120.00"),
                BigDecimal.ZERO
        );

        when(shiftRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shiftService.updateShift(id, request))
                .isInstanceOf(ShiftNotFoundException.class)
                .hasMessage("Shift not found with id: 999");

        verify(shiftRepository, never()).save(any(Shift.class));
    }

    @Test
    void deleteShift_deletesExistingShift() {

        Long id = 1L;
        Shift existingShift = new Shift(
                id,
                "VEA7",
                LocalDate.of(2026, 9, 6),
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                new BigDecimal("120.00"),
                new BigDecimal("35.50")
        );

        when(shiftRepository.findById(id)).thenReturn(Optional.of(existingShift));

        shiftService.deleteShift(id);

        verify(shiftRepository).findById(id);
        verify(shiftRepository).delete(existingShift);
    }

    @Test
    void deleteShift_throwsWhenShiftDoesNotExist() {

        Long id = 999L;
        when(shiftRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> shiftService.deleteShift(id))
                .isInstanceOf(ShiftNotFoundException.class)
                .hasMessage("Shift not found with id: 999");

        verify(shiftRepository, never()).delete(any(Shift.class));
    }
}
