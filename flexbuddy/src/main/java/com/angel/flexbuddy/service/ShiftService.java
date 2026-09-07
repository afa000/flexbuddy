package com.angel.flexbuddy.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.springframework.stereotype.Service;
import com.angel.flexbuddy.dto.CreateShiftRequest;
import com.angel.flexbuddy.dto.ShiftResponse;
import com.angel.flexbuddy.dto.ShiftStatisticsResponse;
import com.angel.flexbuddy.dto.UpdateShiftRequest;
import com.angel.flexbuddy.exception.ShiftNotFoundException;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.ShiftRepository;

@Service
public class ShiftService {

    private final ShiftRepository shiftRepository;
    private final AppUserRepository userRepository;

    public ShiftService(ShiftRepository shiftRepository, AppUserRepository userRepository) {
        this.shiftRepository = shiftRepository;
        this.userRepository = userRepository;
    }

    public List<ShiftResponse> getAllShifts(String email) {
        return shiftRepository.findAllByOwnerEmailIgnoreCaseOrderByDateDescStartTimeDesc(email).stream()
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

    public ShiftStatisticsResponse getShiftStatistics(String email) {

        List<Shift> shifts = shiftRepository.findAllByOwnerEmailIgnoreCaseOrderByDateDescStartTimeDesc(email);

        int totalShifts = shifts.size();
        BigDecimal totalBasePay = BigDecimal.ZERO;
        BigDecimal totalTips = BigDecimal.ZERO;
        int totalTimeWorked = 0;

        for (Shift shift : shifts) {
            totalBasePay = totalBasePay.add(shift.getBasePay());
            totalTips = totalTips.add(shift.getTips());
            totalTimeWorked = totalTimeWorked + shift.getTimeWorked();
        }

        BigDecimal totalEarnings = totalBasePay.add(totalTips);

        BigDecimal averagePayPerShift;

        if (totalShifts == 0) {
            averagePayPerShift = BigDecimal.ZERO;
        }
        else {
            averagePayPerShift = totalEarnings.divide(
                BigDecimal.valueOf(totalShifts),
                2,
                RoundingMode.HALF_UP
            );
        }

        return new ShiftStatisticsResponse(
            totalShifts,
            totalBasePay,
            totalTips,
            totalEarnings,
            averagePayPerShift,
            totalTimeWorked
        );
    }

    public ShiftResponse createShift(String email, CreateShiftRequest request) {

        Shift shift = new Shift();

        AppUser owner = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalStateException("Signed-in account could not be found."));

        shift.setStation(request.getStation());
        shift.setDate(request.getDate());
        shift.setStartTime(request.getStartTime());
        shift.setEndTime(request.getEndTime());
        shift.setBasePay(request.getBasePay());
        shift.setTips(request.getTips());
        shift.setOwner(owner);

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


    public ShiftResponse updateShift(String email, Long id, UpdateShiftRequest request) {

        Shift shift = shiftRepository.findByIdAndOwnerEmailIgnoreCase(id, email)
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

    public void deleteShift(String email, Long id) {
        Shift shift = shiftRepository.findByIdAndOwnerEmailIgnoreCase(id, email)
                .orElseThrow(() -> new ShiftNotFoundException(id));

        shiftRepository.delete(shift);
    }

}
