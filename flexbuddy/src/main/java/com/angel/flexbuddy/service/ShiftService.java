package com.angel.flexbuddy.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;
import java.util.Map;
import java.util.stream.Collectors;

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
import com.angel.flexbuddy.model.Expense;
import com.angel.flexbuddy.dto.AccountSettingsResponse;
import com.angel.flexbuddy.dto.NetEarningsResult;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.ShiftRepository;

@Service
public class ShiftService {

    private static final LocalDate EARLIEST_DATE = LocalDate.of(1, 1, 1);
    private static final LocalDate LATEST_DATE = LocalDate.of(9999, 12, 31);

    private final ShiftRepository shiftRepository;
    private final AppUserRepository userRepository;
    private final Clock clock;
    private final ExpenseService expenseService;
    private final AccountSettingsService settingsService;
    private final NetEarningsCalculator netCalculator;

    public ShiftService(ShiftRepository shiftRepository, AppUserRepository userRepository, Clock clock,
            ExpenseService expenseService, AccountSettingsService settingsService, NetEarningsCalculator netCalculator) {
        this.shiftRepository = shiftRepository;
        this.userRepository = userRepository;
        this.clock = clock;
        this.expenseService = expenseService;
        this.settingsService = settingsService;
        this.netCalculator = netCalculator;
    }

    public List<ShiftResponse> getAllShifts(String email) {
        return getShifts(email, ShiftFilter.report(null, null, null, null));
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<ShiftResponse> getShifts(String email, ShiftFilter filter) {
        List<Shift> shifts = findFiltered(email, filter);
        AccountSettingsResponse settings = settingsService.get(email);
        Map<Long, List<Expense>> expenses = expenseService.findForShifts(email, shifts).stream()
                .collect(Collectors.groupingBy(expense -> expense.getShift().getId()));
        List<ShiftResponse> responses = shifts.stream().map(shift -> toResponse(shift,
                expenses.getOrDefault(shift.getId(), List.of()), settings)).toList();
        return responses.stream().sorted(responseComparator(filter)).toList();
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
                request.getBasePay(), request.getTips(), request.getMiles());
        shift.setOwner(owner);
        return toResponse(shiftRepository.save(shift), List.of(), settingsService.get(email));
    }

    public ShiftResponse updateShift(String email, Long id, UpdateShiftRequest request) {
        Shift shift = shiftRepository.findByIdAndOwnerEmailIgnoreCase(id, email)
                .orElseThrow(() -> new ShiftNotFoundException(id));
        applyRequest(shift, request.getStation(), request.getDate(), request.getStartTime(), request.getEndTime(),
                request.getBasePay(), request.getTips(), request.getMiles());
        Shift saved = shiftRepository.save(shift);
        return toResponse(saved, expenseService.findForShifts(email, List.of(saved)), settingsService.get(email));
    }

    @org.springframework.transaction.annotation.Transactional
    public String deleteShift(String email, Long id) {
        String batch = UUID.randomUUID().toString();
        if (shiftRepository.softDelete(email, id, Instant.now(clock), batch) == 0) {
            throw new ShiftNotFoundException(id);
        }
        return batch;
    }

    @org.springframework.transaction.annotation.Transactional
    public ShiftResponse restoreShift(String email, Long id) {
        int updated = shiftRepository.restoreDeleted(email, id);
        if (updated == 0) throw new ShiftNotFoundException(id);
        return shiftRepository.findByIdAndOwnerEmailIgnoreCase(id, email)
                .map(shift -> toResponse(shift, expenseService.findForShifts(email, List.of(shift)), settingsService.get(email)))
                .orElseThrow(() -> new ShiftNotFoundException(id));
    }

    @org.springframework.transaction.annotation.Transactional
    public int restoreBatch(String email, String batch) {
        return shiftRepository.restoreBatch(email, batch);
    }

    @org.springframework.transaction.annotation.Transactional
    public List<ShiftResponse> getTrash(String email) {
        shiftRepository.purgeDeletedBefore(email, Instant.now(clock).minus(30, java.time.temporal.ChronoUnit.DAYS));
        AccountSettingsResponse settings = settingsService.get(email);
        return shiftRepository.findTrash(email).stream().map(shift -> toResponse(shift, List.of(), settings)).toList();
    }

    @org.springframework.transaction.annotation.Transactional
    public void permanentlyDelete(String email, Long id) {
        if (shiftRepository.permanentlyDelete(email, id) == 0) throw new ShiftNotFoundException(id);
    }

    @org.springframework.transaction.annotation.Transactional
    public int emptyTrash(String email) {
        return shiftRepository.emptyTrash(email);
    }

    private void applyRequest(Shift shift, String station, LocalDate date, LocalTime startTime, LocalTime endTime,
            BigDecimal basePay, BigDecimal tips, BigDecimal miles) {
        shift.setStation(station.trim());
        shift.setDate(date);
        shift.setStartTime(startTime);
        shift.setEndTime(endTime);
        shift.setBasePay(basePay);
        shift.setTips(tips);
        shift.setMiles(miles);
    }

    private ShiftResponse toResponse(Shift shift, List<Expense> expenses, AccountSettingsResponse settings) {
        NetEarningsResult net = netCalculator.calculate(List.of(shift), expenses,
                settings.vehicleCostMethod(), settings.mileageRate());
        return new ShiftResponse(
                shift.getId(), shift.getStation(), shift.getDate(), shift.getStartTime(), shift.getEndTime(),
                shift.getBasePay(), shift.getTips(), shift.getTotalPay(), shift.getTimeWorked(), shift.getHourlyRate(),
                shift.getMiles(), shift.getMileageCost(settings.mileageRate()), shift.getEarningsPerMile(),
                net.cashSpent(), net.netEarnings(), net.netHourlyRate(),
                shift.getCreatedAt(), shift.getUpdatedAt(), shift.getDeletedAt()
        );
    }

    private Comparator<ShiftResponse> responseComparator(ShiftFilter filter) {
        Comparator<ShiftResponse> newestFirst = Comparator
                .comparing(ShiftResponse::getDate, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(ShiftResponse::getStartTime, Comparator.nullsLast(Comparator.reverseOrder()));
        Comparator<ShiftResponse> primary = switch (filter.sort()) {
            case DATE -> Comparator.comparing(ShiftResponse::getDate, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(ShiftResponse::getStartTime, Comparator.nullsLast(Comparator.naturalOrder()));
            case STATION -> Comparator.comparing(ShiftResponse::getStation, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
            case BASE_PAY -> Comparator.comparing(response -> money(response.getBasePay()));
            case TIPS -> Comparator.comparing(response -> money(response.getTips()));
            case TOTAL_PAY -> Comparator.comparing(ShiftResponse::getTotalPay);
            case TIME_WORKED -> Comparator.comparingInt(ShiftResponse::getTimeWorked);
            case HOURLY_RATE -> Comparator.comparing(ShiftResponse::getHourlyRate);
            case MILES -> Comparator.comparing(response -> money(response.getMiles()));
            case NET_PAY -> Comparator.comparing(ShiftResponse::getNetPay);
            case NET_HOURLY_RATE -> Comparator.comparing(ShiftResponse::getNetHourlyRate);
            case CREATED_AT -> Comparator.comparing(ShiftResponse::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
            case UPDATED_AT -> Comparator.comparing(ShiftResponse::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
        };
        if (filter.direction() == SortDirection.DESC) primary = primary.reversed();
        return primary.thenComparing(newestFirst);
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
