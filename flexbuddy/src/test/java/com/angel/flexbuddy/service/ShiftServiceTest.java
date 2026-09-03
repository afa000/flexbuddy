package com.angel.flexbuddy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.angel.flexbuddy.dto.CreateShiftRequest;
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
                "Grill",
                LocalDate.of(2026, 9, 3),
                LocalTime.of(9, 0),
                LocalTime.of(17, 0),
                new BigDecimal("120.00"),
                new BigDecimal("35.50"));

        when(shiftRepository.save(any(Shift.class))).thenAnswer(inv -> inv.getArgument(0));

        Shift result = shiftService.createShift(request);

        ArgumentCaptor<Shift> captor = ArgumentCaptor.forClass(Shift.class);
        verify(shiftRepository).save(captor.capture());
        Shift saved = captor.getValue();

        assertThat(saved.getStation()).isEqualTo("Grill");
        assertThat(saved.getDate()).isEqualTo(LocalDate.of(2026, 9, 3));
        assertThat(saved.getStartTime()).isEqualTo(LocalTime.of(9, 0));
        assertThat(saved.getEndTime()).isEqualTo(LocalTime.of(17, 0));
        assertThat(saved.getBasePay()).isEqualByComparingTo("120.00");
        assertThat(saved.getTips()).isEqualByComparingTo("35.50");

        assertThat(result.getTotalPay()).isEqualByComparingTo("155.50");
    }
}
