package com.angel.flexbuddy.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "shift", indexes = {
        @Index(name = "idx_shift_owner_date", columnList = "owner_id,date"),
        @Index(name = "idx_shift_owner_status_date", columnList = "owner_id,status,date")
})
@EntityListeners(TimestampListener.class)
@SQLRestriction("deleted_at is null")
@Getter
@Setter
@NoArgsConstructor
public class Shift implements Timestamped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String station;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false)
    private LocalTime startTime;

    @Column(nullable = false)
    private LocalTime endTime;

    @Column(nullable = false)
    private BigDecimal basePay;

    @Column(nullable = false)
    private BigDecimal tips;

    @Column(precision = 8, scale = 1)
    private BigDecimal miles;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ShiftStatus status = ShiftStatus.COMPLETED;

    private Instant statusChangedAt;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant deletedAt;

    @Column(length = 36)
    private String deleteBatch;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private AppUser owner;

    public Shift(
            Long id,
            String station,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            BigDecimal basePay,
            BigDecimal tips
    ) {
        this(id, station, date, startTime, endTime, basePay, tips, null);
    }

    public Shift(
            Long id,
            String station,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            BigDecimal basePay,
            BigDecimal tips,
            AppUser owner
    ) {
        this.id = id;
        this.station = station;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.basePay = basePay;
        this.tips = tips;
        this.owner = owner;
    }

    public BigDecimal getTotalPay() {
        BigDecimal base;
        if (basePay != null) {
            base = basePay;
        }
        else {
            base = BigDecimal.ZERO;
        }

        BigDecimal tip;
        if (tips != null) {
            tip = tips;
        }
        else {
            tip = BigDecimal.ZERO;
        }

        return base.add(tip);
    }

    public int getTimeWorked() {
        if (startTime == null || endTime == null) {
            return 0;
        }
        int totalMinutes = (int) ChronoUnit.MINUTES.between(startTime, endTime);
        return totalMinutes < 0 ? totalMinutes + (24 * 60) : totalMinutes;
    }

    public BigDecimal getHourlyRate() {
        int minutes = getTimeWorked();
        if (minutes == 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return getTotalPay()
                .multiply(BigDecimal.valueOf(60))
                .divide(BigDecimal.valueOf(minutes), 2, RoundingMode.HALF_UP);
    }

    public BigDecimal getMileageCost(BigDecimal mileageRate) {
        if (miles == null || mileageRate == null) return BigDecimal.ZERO.setScale(2);
        return miles.multiply(mileageRate).setScale(2, RoundingMode.HALF_UP);
    }

    public BigDecimal getEarningsPerMile() {
        if (miles == null || miles.signum() == 0) return null;
        return getTotalPay().divide(miles, 2, RoundingMode.HALF_UP);
    }

    /** Completed shifts, and cancelled blocks that paid cancellation pay, earn money. Scheduled and forfeited ones do not. */
    public boolean countsTowardEarnings() {
        return effectiveStatus() == ShiftStatus.COMPLETED || effectiveStatus() == ShiftStatus.CANCELLED;
    }

    /** Only a worked block adds hours. */
    public boolean countsTowardHours() {
        return effectiveStatus() == ShiftStatus.COMPLETED;
    }

    /** The drive to the station happened for every block except one that is still scheduled. */
    public boolean countsTowardMiles() {
        return effectiveStatus() != ShiftStatus.SCHEDULED;
    }

    public BigDecimal getEarnedBasePay() {
        return countsTowardEarnings() && basePay != null ? basePay : BigDecimal.ZERO;
    }

    public BigDecimal getEarnedTips() {
        return effectiveStatus() == ShiftStatus.COMPLETED && tips != null ? tips : BigDecimal.ZERO;
    }

    public BigDecimal getEarnedPay() {
        return getEarnedBasePay().add(getEarnedTips());
    }

    public int getWorkedMinutes() {
        return countsTowardHours() ? getTimeWorked() : 0;
    }

    public BigDecimal getCountedMiles() {
        return countsTowardMiles() ? miles : null;
    }

    public LocalDateTime getStartDateTime() {
        return date == null || startTime == null ? null : LocalDateTime.of(date, startTime);
    }

    public LocalDateTime getEndDateTime() {
        LocalDateTime start = getStartDateTime();
        return start == null || endTime == null ? null : start.plusMinutes(getTimeWorked());
    }

    private ShiftStatus effectiveStatus() {
        return status == null ? ShiftStatus.COMPLETED : status;
    }

}
