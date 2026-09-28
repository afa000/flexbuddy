package com.angel.flexbuddy.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

import com.angel.flexbuddy.dto.BlockDetailsRequest;
import com.angel.flexbuddy.dto.BlockDetailsResponse;
import com.angel.flexbuddy.dto.CreateShiftRequest;
import com.angel.flexbuddy.dto.OdometerReadingResponse;
import com.angel.flexbuddy.dto.ShiftFilter;
import com.angel.flexbuddy.dto.ShiftResponse;
import com.angel.flexbuddy.dto.ShiftStatusRequest;
import com.angel.flexbuddy.dto.ShiftSort;
import com.angel.flexbuddy.dto.SortDirection;
import com.angel.flexbuddy.dto.UpdateShiftRequest;
import com.angel.flexbuddy.exception.InvalidShiftException;
import com.angel.flexbuddy.exception.ShiftNotFoundException;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.model.ShiftStatus;
import com.angel.flexbuddy.model.Expense;
import com.angel.flexbuddy.dto.AccountSettingsResponse;
import com.angel.flexbuddy.dto.NetEarningsResult;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.ShiftRepository;

@Service
public class ShiftService {

    private static final LocalDate EARLIEST_DATE = LocalDate.of(1, 1, 1);
    private static final LocalDate LATEST_DATE = LocalDate.of(9999, 12, 31);
    /** A block can be started this long before its scheduled start, for drivers who arrive early. */
    static final int EARLIEST_START_MINUTES = 120;

    private final ShiftRepository shiftRepository;
    private final AppUserRepository userRepository;
    private final Clock clock;
    private final ExpenseService expenseService;
    private final AccountSettingsService settingsService;
    private final NetEarningsCalculator netCalculator;
    private final UserTimeService userTime;

    public ShiftService(ShiftRepository shiftRepository, AppUserRepository userRepository, Clock clock,
            ExpenseService expenseService, AccountSettingsService settingsService, NetEarningsCalculator netCalculator,
            UserTimeService userTime) {
        this.shiftRepository = shiftRepository;
        this.userRepository = userRepository;
        this.clock = clock;
        this.expenseService = expenseService;
        this.settingsService = settingsService;
        this.netCalculator = netCalculator;
        this.userTime = userTime;
    }

    public List<ShiftResponse> getAllShifts(String email) {
        return getShifts(email, ShiftFilter.report(null, null, null, null));
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<ShiftResponse> getShifts(String email, ShiftFilter filter) {
        return toResponses(email, findFiltered(email, filter)).stream().sorted(responseComparator(filter)).toList();
    }

    /** Maps shifts to responses in their original order, loading settings and linked expenses once. */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<ShiftResponse> toResponses(String email, List<Shift> shifts) {
        AccountSettingsResponse settings = settingsService.get(email);
        Map<Long, List<Expense>> expenses = expenseService.findForShifts(email, shifts).stream()
                .collect(Collectors.groupingBy(expense -> expense.getShift().getId()));
        return shifts.stream().map(shift -> toResponse(shift,
                expenses.getOrDefault(shift.getId(), List.of()), settings)).toList();
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
                filter.query() == null ? "" : filter.query(),
                filter.statuses()
        );
    }

    /** Scheduled shifts dated between the bounds, oldest first. A null bound is open-ended. */
    public List<Shift> findScheduled(String email, LocalDate from, LocalDate to) {
        return shiftRepository.findByOwnerEmailIgnoreCaseAndStatusAndDateBetweenOrderByDateAscStartTimeAsc(
                email, ShiftStatus.SCHEDULED, from == null ? EARLIEST_DATE : from, to == null ? LATEST_DATE : to);
    }

    public ShiftResponse createShift(String email, CreateShiftRequest request) {
        AppUser owner = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalStateException("Signed-in account could not be found."));

        Shift shift = new Shift();
        ShiftStatus status = request.getStatus() == null ? ShiftStatus.COMPLETED : request.getStatus();
        applyRequest(shift, status, request.getStation(), request.getDate(), request.getStartTime(),
                request.getEndTime(), request.getBasePay(), request.getTips(), request.getMiles());
        applyDetails(shift, request.getDetails() == null ? BlockDetailsRequest.EMPTY : request.getDetails(),
                request.getMiles());
        shift.setOwner(owner);
        return toResponse(shiftRepository.save(shift), List.of(), settingsService.get(email));
    }

    public ShiftResponse updateShift(String email, Long id, UpdateShiftRequest request) {
        Shift shift = shiftRepository.findByIdAndOwnerEmailIgnoreCase(id, email)
                .orElseThrow(() -> new ShiftNotFoundException(id));
        ShiftStatus status = request.getStatus() == null ? shift.getStatus() : request.getStatus();
        applyRequest(shift, status, request.getStation(), request.getDate(), request.getStartTime(),
                request.getEndTime(), request.getBasePay(), request.getTips(), request.getMiles());
        applyDetails(shift, request.getDetails(), request.getMiles());
        Shift saved = shiftRepository.save(shift);
        return toResponse(saved, expenseService.findForShifts(email, List.of(saved)), settingsService.get(email));
    }

    /**
     * Moves a shift to another status, keeping its id and linked expenses. Pay fields that are not supplied fall
     * back to what makes sense for the target: a completed block keeps its offered pay, while cancelled and
     * forfeited blocks start at zero so the offer is never counted as earned.
     */
    @org.springframework.transaction.annotation.Transactional
    public ShiftResponse changeStatus(String email, Long id, ShiftStatusRequest request) {
        Shift shift = shiftRepository.findByIdAndOwnerEmailIgnoreCase(id, email)
                .orElseThrow(() -> new ShiftNotFoundException(id));
        ShiftStatus target = request.status();
        boolean unchanged = target == shift.getStatus();
        BigDecimal basePay = request.basePay() != null ? request.basePay()
                : unchanged || target == ShiftStatus.COMPLETED ? shift.getBasePay() : BigDecimal.ZERO;
        BigDecimal tips = request.tips() != null ? request.tips()
                : target == ShiftStatus.COMPLETED ? shift.getTips() : BigDecimal.ZERO;
        BigDecimal miles = request.miles() != null ? request.miles()
                : target == ShiftStatus.SCHEDULED ? null : shift.getMiles();
        applyRequest(shift, target, shift.getStation(), shift.getDate(), shift.getStartTime(), shift.getEndTime(),
                basePay, tips, miles);
        applyDetails(shift, request.details(), request.miles());
        Shift saved = shiftRepository.save(shift);
        return toResponse(saved, expenseService.findForShifts(email, List.of(saved)), settingsService.get(email));
    }

    /**
     * The most recent end reading from a block that started before the given moment, or before now when no moment
     * is given, so a new block's start reading can be filled in.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public java.util.Optional<OdometerReadingResponse> latestOdometer(String email, java.time.LocalDateTime before) {
        java.time.LocalDateTime moment = before == null ? userTime.now(email) : before;
        return shiftRepository.findWithOdometerBefore(email, moment.toLocalDate(), moment.toLocalTime(),
                        org.springframework.data.domain.PageRequest.of(0, 1)).stream().findFirst()
                .map(shift -> new OdometerReadingResponse(shift.getOdometerEnd(), shift.getDate(), shift.getStation()));
    }

    /** Records that a scheduled block has started now, in the driver's time zone. */
    @org.springframework.transaction.annotation.Transactional
    public ShiftResponse startShift(String email, Long id) {
        Shift shift = shiftRepository.findByIdAndOwnerEmailIgnoreCase(id, email)
                .orElseThrow(() -> new ShiftNotFoundException(id));
        if (shift.getStatus() != ShiftStatus.SCHEDULED) {
            throw new InvalidShiftException("Only a scheduled block can be started.");
        }
        java.time.LocalDateTime now = userTime.now(email).truncatedTo(java.time.temporal.ChronoUnit.MINUTES);
        if (now.isBefore(shift.getStartDateTime().minusMinutes(EARLIEST_START_MINUTES))) {
            throw new InvalidShiftException("A block can be started from 2 hours before its scheduled start.");
        }
        if (now.isAfter(shift.getEndDateTime())) {
            throw new InvalidShiftException("This block's scheduled time has passed. Mark it completed instead.");
        }
        shift.setActualStart(now.toLocalTime());
        shift.setActualEnd(null);
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

    private void applyRequest(Shift shift, ShiftStatus status, String station, LocalDate date, LocalTime startTime,
            LocalTime endTime, BigDecimal basePay, BigDecimal tips, BigDecimal miles) {
        ShiftStatus current = shift.getStatus() == null ? ShiftStatus.COMPLETED : shift.getStatus();
        boolean existing = shift.getId() != null;
        if (existing && status == ShiftStatus.SCHEDULED && current != ShiftStatus.SCHEDULED) {
            throw new InvalidShiftException("A " + current.label()
                    + " shift cannot be moved back to scheduled. Delete it and add the block again.");
        }
        String problem = status.validate(basePay, tips, miles);
        if (problem != null) throw new InvalidShiftException(problem);
        if (existing && current != status) shift.setStatusChangedAt(Instant.now(clock));
        shift.setStatus(status);
        shift.setStation(station.trim());
        shift.setDate(date);
        shift.setStartTime(startTime);
        shift.setEndTime(endTime);
        shift.setBasePay(basePay);
        shift.setTips(tips);
        shift.setMiles(miles);
    }

    /**
     * Replaces the block's details when the request carries them. Without them, a block that was cancelled or
     * forfeited loses its actual times, because it was not worked; every other block keeps what it had. When both
     * odometer readings are given and no miles were typed, the miles become the distance between them.
     */
    private void applyDetails(Shift shift, BlockDetailsRequest details, BigDecimal requestedMiles) {
        boolean worked = shift.getStatus() == ShiftStatus.COMPLETED || shift.getStatus() == ShiftStatus.SCHEDULED;
        if (details != null) {
            shift.setActualStart(details.actualStart());
            shift.setActualEnd(details.actualEnd());
            shift.setOdometerStart(details.odometerStart());
            shift.setOdometerEnd(details.odometerEnd());
            shift.setStops(details.stops());
            shift.setPackages(details.packages());
            shift.setReturns(details.returns());
        } else if (!worked) {
            shift.setActualStart(null);
            shift.setActualEnd(null);
            shift.setStops(null);
            shift.setPackages(null);
            shift.setReturns(null);
        }
        applyOdometer(shift, requestedMiles);
        checkRoute(shift);
        if (shift.getActualStart() == null && shift.getActualEnd() == null) return;
        if (!worked) throw new InvalidShiftException("Actual times are only recorded for completed blocks.");
        if (shift.getActualStart() == null) throw new InvalidShiftException("Enter when the block started.");
        if (shift.getStatus() == ShiftStatus.SCHEDULED && shift.getActualEnd() != null) {
            throw new InvalidShiftException("Mark the block completed to record when it finished.");
        }
        if (shift.getActualStart().equals(shift.getActualEnd())) {
            throw new InvalidShiftException("The finish time must be after the start time.");
        }
    }

    private static void checkRoute(Shift shift) {
        if (shift.getStops() == null && shift.getPackages() == null && shift.getReturns() == null) return;
        if (shift.getStatus() != ShiftStatus.COMPLETED) {
            throw new InvalidShiftException("Stops, packages, and returns are only recorded for completed blocks.");
        }
        if (shift.getReturns() != null && shift.getPackages() != null && shift.getReturns() > shift.getPackages()) {
            throw new InvalidShiftException("Returns cannot be more than the packages carried.");
        }
    }

    private static void applyOdometer(Shift shift, BigDecimal requestedMiles) {
        BigDecimal start = shift.getOdometerStart();
        BigDecimal end = shift.getOdometerEnd();
        if (start == null && end == null) return;
        if (shift.getStatus() == ShiftStatus.SCHEDULED) {
            throw new InvalidShiftException("Record the odometer when the block is finished.");
        }
        if (start == null || end == null) return;
        if (end.compareTo(start) < 0) {
            throw new InvalidShiftException("The odometer end reading must be at least the start reading.");
        }
        if (requestedMiles == null) shift.setMiles(end.subtract(start).setScale(1, RoundingMode.HALF_UP));
    }

    private ShiftResponse toResponse(Shift shift, List<Expense> expenses, AccountSettingsResponse settings) {
        NetEarningsResult net = netCalculator.calculate(List.of(shift), expenses,
                settings.vehicleCostMethod(), settings.mileageRate());
        return new ShiftResponse(
                shift.getId(), shift.getStation(), shift.getDate(), shift.getStartTime(), shift.getEndTime(),
                shift.getBasePay(), shift.getTips(), shift.getTotalPay(), shift.getTimeWorked(), shift.getHourlyRate(),
                shift.getMiles(), shift.getMileageCost(settings.mileageRate()), shift.getEarningsPerMile(),
                net.cashSpent(), net.netEarnings(), net.netHourlyRate(),
                shift.getCreatedAt(), shift.getUpdatedAt(), shift.getDeletedAt(),
                shift.getStatus(), shift.getStatusChangedAt(), shift.getEarnedPay().setScale(2, RoundingMode.HALF_UP),
                details(shift)
        );
    }

    private static BlockDetailsResponse details(Shift shift) {
        Integer actual = shift.countsTowardHours() ? shift.getActualMinutes() : null;
        BigDecimal actualHourly = actual == null || actual == 0 ? null : shift.getEarnedPay()
                .multiply(BigDecimal.valueOf(60)).divide(BigDecimal.valueOf(actual), 2, RoundingMode.HALF_UP);
        return new BlockDetailsResponse(shift.getActualStart(), shift.getActualEnd(), actual, actualHourly,
                shift.getFinishedEarlyMinutes(), shift.getOdometerStart(), shift.getOdometerEnd(), shift.getStops(),
                shift.getPackages(), shift.getReturns(), shift.getMinutesPerStop(), shift.getReturnsRate());
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
            case STOPS -> Comparator.comparingInt(response -> response.getDetails().stops() == null ? 0
                    : response.getDetails().stops());
            case MINUTES_PER_STOP -> Comparator.comparing(response -> money(response.getDetails().minutesPerStop()));
            case NET_PAY -> Comparator.comparing(ShiftResponse::getNetPay);
            case NET_HOURLY_RATE -> Comparator.comparing(ShiftResponse::getNetHourlyRate);
            case CREATED_AT -> Comparator.comparing(ShiftResponse::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
            case UPDATED_AT -> Comparator.comparing(ShiftResponse::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
        };
        if (filter.direction() == SortDirection.DESC) primary = primary.reversed();
        // Blocks without route counts go last in either direction instead of sorting as zero.
        if (filter.sort() == ShiftSort.STOPS) {
            primary = Comparator.comparing((ShiftResponse response) -> response.getDetails().stops() == null).thenComparing(primary);
        } else if (filter.sort() == ShiftSort.MINUTES_PER_STOP) {
            primary = Comparator.comparing((ShiftResponse response) -> response.getDetails().minutesPerStop() == null)
                    .thenComparing(primary);
        }
        return primary.thenComparing(newestFirst);
    }

    private BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
