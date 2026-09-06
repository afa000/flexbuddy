package com.angel.flexbuddy.service;

import java.util.List;
import org.springframework.stereotype.Service;
import com.angel.flexbuddy.dto.CreateShiftRequest;
import com.angel.flexbuddy.dto.ShiftResponse;
import com.angel.flexbuddy.dto.UpdateShiftRequest;
import com.angel.flexbuddy.exception.ShiftNotFoundException;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.repository.ShiftRepository;

@Service
public class ShiftService {

    private final ShiftRepository shiftRepository;

    public ShiftService(ShiftRepository shiftRepository) {
        this.shiftRepository = shiftRepository;
    }

    public List<ShiftResponse> getAllShifts() {
        return shiftRepository.findAll().stream()
                .map(shift -> new ShiftResponse(
                        shift.getId(),
                        shift.getStation(),
                        shift.getDate(),
                        shift.getStartTime(),
                        shift.getEndTime(),
                        shift.getBasePay(),
                        shift.getTips(),
                        shift.getTotalPay()
                ))
                .toList();
    }

    public ShiftResponse createShift(CreateShiftRequest request) {

        Shift shift = new Shift();

        shift.setStation(request.getStation());
        shift.setDate(request.getDate());
        shift.setStartTime(request.getStartTime());
        shift.setEndTime(request.getEndTime());
        shift.setBasePay(request.getBasePay());
        shift.setTips(request.getTips());

        Shift updatedShift = shiftRepository.save(shift);

        return new ShiftResponse(

                updatedShift.getId(),
                updatedShift.getStation(),
                updatedShift.getDate(),
                updatedShift.getStartTime(),
                updatedShift.getEndTime(),
                updatedShift.getBasePay(),
                updatedShift.getTips(),
                updatedShift.getTotalPay()
        );
    }


    public ShiftResponse updateShift(Long id, UpdateShiftRequest request) {

        Shift shift = shiftRepository.findById(id)
                .orElseThrow(() -> new ShiftNotFoundException(id));

        
        shift.setStation(request.getStation());
        shift.setDate(request.getDate());
        shift.setStartTime(request.getStartTime());
        shift.setEndTime(request.getEndTime());
        shift.setBasePay(request.getBasePay());
        shift.setTips(request.getTips());

        Shift updatedShift = shiftRepository.save(shift);

        return new ShiftResponse(

                updatedShift.getId(),
                updatedShift.getStation(),
                updatedShift.getDate(),
                updatedShift.getStartTime(),
                updatedShift.getEndTime(),
                updatedShift.getBasePay(),
                updatedShift.getTips(),
                updatedShift.getTotalPay()
        );
    }

    public void deleteShift(Long id) {
        Shift shift = shiftRepository.findById(id)
                .orElseThrow(() -> new ShiftNotFoundException(id));

        shiftRepository.delete(shift);
    }

}
