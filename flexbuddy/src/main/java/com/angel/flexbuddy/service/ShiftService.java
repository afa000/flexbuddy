package com.angel.flexbuddy.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.angel.flexbuddy.dto.CreateShiftRequest;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.repository.ShiftRepository;

@Service
public class ShiftService {

    private final ShiftRepository shiftRepository;

    public ShiftService(ShiftRepository shiftRepository) {
        this.shiftRepository = shiftRepository;
    }

    public List<Shift> getAllShifts() {
        return shiftRepository.findAll();
    }

    public Shift createShift(CreateShiftRequest request) {

        Shift shift = new Shift();

        shift.setStation(request.getStation());
        shift.setDate(request.getDate());
        shift.setStartTime(request.getStartTime());
        shift.setEndTime(request.getEndTime());
        shift.setBasePay(request.getBasePay());
        shift.setTips(request.getTips());

        return shiftRepository.save(shift);
    }

    public Shift updateShift(Long id, Shift updatedShift) {
        return shiftRepository.findById(id)
                .map(shift -> {
                    if (updatedShift.getStation() != null) {
                        shift.setStation(updatedShift.getStation());
                    }
                    if (updatedShift.getDate() != null) {
                        shift.setDate(updatedShift.getDate());
                    }
                    if (updatedShift.getStartTime() != null) {
                        shift.setStartTime(updatedShift.getStartTime());
                    }
                    if (updatedShift.getEndTime() != null) {
                        shift.setEndTime(updatedShift.getEndTime());
                    }
                    if (updatedShift.getBasePay() != null) {
                        shift.setBasePay(updatedShift.getBasePay());
                    }
                    if (updatedShift.getTips() != null) {
                        shift.setTips(updatedShift.getTips());
                    }
                    return shiftRepository.save(shift);
                })
                .orElseThrow(() -> new RuntimeException("Shift not found with id " + id));
    }

    public void deleteShift(Long id) {
        shiftRepository.deleteById(id);
    }

}
