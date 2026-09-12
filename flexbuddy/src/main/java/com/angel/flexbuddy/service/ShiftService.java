package com.angel.flexbuddy.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

import org.springframework.stereotype.Service;

import com.angel.flexbuddy.dto.CreateShiftRequest;
import com.angel.flexbuddy.dto.ShiftFilter;
import com.angel.flexbuddy.dto.ShiftResponse;
import com.angel.flexbuddy.dto.ShiftSort;
import com.angel.flexbuddy.dto.SortDirection;
import com.angel.flexbuddy.dto.UpdateShiftRequest;
import com.angel.flexbuddy.exception.ShiftNotFoundException;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.ShiftRepository;

@Service
public class ShiftService {

    private static final LocalDate EARLIEST_DATE = LocalDate.of(1, 1, 1);
    private static final LocalDate LATEST_DATE = LocalDate.of(9999, 12, 31);

    private final ShiftRepository shiftRepository;
    private final AppUserRepository userRepository;

    public ShiftService(ShiftRepository shiftRepository, AppUserRepository userRepository) {
        this.shiftRepository = shiftRepository;
        this.userRepository = userRepository;
    }

    public List<ShiftResponse> getAllShifts(String email) {
        return getShifts(email, ShiftFilter.report(null, null, null, null));
    }

    public List<ShiftResponse> getShifts(String email, ShiftFilter filter) {
        return findFiltered(email, filter).stream()
                .sorted(comparator(filter))
                .map(this::toResponse)
                .toList();
    }

    public List<String> getStations(String email) {
        TreeSet<String> stations = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        shiftRepository.findDistinctStations(email).stream()
                .filter(station -> station != null && !station.isBlank())
                .map(String::trim)
                .forEach(stations::add);
        return List.copyOf(stations);
    }

    public List<Shift> findFiltered(String email, ShiftFilter filter) {
        return shiftRepository.findFiltered(
                email,
                filter.from() == null ? EARLIEST_DATE : filter.from(),
                filter.to() == null ? LATEST_DATE : filter.to(),
                filter.station() == null ? "" : filter.station(),
                filter.query() == null ? "" : filter.query()
        );
    }

    public ShiftResponse createShift(String email, CreateShiftRequest request) {
        AppUser owner = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalStateException("Signed-in account could not be found."));

        Shift shift = new Shift();
        applyRequest(shift, request.getStation(), request.getDate(), request.getStartTime(), request.getEndTime(),
                request.getBasePay(), request.getTips());
        shift.setOwner(owner);
        return toResponse(shiftRepository.save(shift));
    }

    public ShiftResponse updateShift(String email, Long id, UpdateShiftRequest request) {
        Shift shift = shiftRepository.findByIdAndOwnerEmailIgnoreCase(id, email)
                .orElseThrow(() -> new ShiftNotFoundException(id));
        applyRequest(shift, request.getStation(), request.getDate(), request.getStartTime(), request.getEndTime(),
                request.getBasePay(), request.getTips());
        return toResponse(shiftRepository.save(shift));
    }

    public void deleteShift(String email, Long id) {
        Shift shift = shiftRepository.findByIdAndOwnerEmailIgnoreCase(id, email)
                .orElseThrow(() -> new ShiftNotFoundException(id));
        shiftRepository.delete(shift);
    }

    private void applyRequest(Shift shift, String station, LocalDate date, LocalTime startTime, LocalTime endTime,
            BigDecimal basePay, BigDecimal tips) {
        shift.setStation(station.trim());
        shift.setDate(date);
        shift.setStartTime(startTime);
        shift.setEndTime(endTime);
        shift.setBasePay(basePay);
        shift.setTips(tips);
    }

    private ShiftResponse toResponse(Shift shift) {
        return new ShiftResponse(
                shift.getId(), shift.getStation(), shift.getDate(), shift.getStartTime(), shift.getEndTime(),
                shift.getBasePay(), shift.getTips(), shift.getTotalPay(), shift.getTimeWorked(), shift.getHourlyRate()
        );
    }

    private Comparator<Shift> comparator(ShiftFilter filter) {
        Comparator<Shift> newestFirst = Comparator
                .comparing(Shift::getDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(Shift::getStartTime, Comparator.nullsLast(Comparator.reverseOrder()));

        if (filter.sort() == ShiftSort.DATE) {
            Comparator<Shift> dateOrder = Comparator
                    .comparing(Shift::getDate, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(Shift::getStartTime, Comparator.nullsLast(Comparator.naturalOrder()));
            return filter.direction() == SortDirection.DESC ? dateOrder.reversed() : dateOrder;
        }

        Comparator<Shift> primary = switch (filter.sort()) {
            case STATION -> Comparator.comparing(Shift::getStation,
                    Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case BASE_PAY -> Comparator.comparing(shift -> money(shift.getBasePay()));
            case TIPS -> Comparator.comparing(shift -> money(shift.getTips()));
            case TOTAL_PAY -> Comparator.comparing(Shift::getTotalPay);
            case TIME_WORKED -> Comparator.comparingInt(Shift::getTimeWorked);
            case HOURLY_RATE -> Comparator.comparing(Shift::getHourlyRate);
            case DATE -> throw new IllegalStateException("Date sorting is handled above.");
        };
        if (filter.direction() == SortDirection.DESC) primary = primary.reversed();
        return primary.thenComparing(newestFirst);
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
