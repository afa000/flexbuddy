package com.angel.flexbuddy.model;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Records that an estimated-tax reminder was sent for an account and a due date, so it goes out at most once. The
 * owner is a plain id, as {@link ReminderLog} keeps a shift id, so claiming a reminder never loads the user.
 */
@Entity
@Table(name = "tax_reminder_log")
@IdClass(TaxReminderLog.Key.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class TaxReminderLog {

    @Id
    private Long ownerId;

    @Id
    private LocalDate dueDate;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private TaxReminderKind kind;

    @Column(nullable = false)
    private Instant sentAt;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private Long ownerId;
        private LocalDate dueDate;
        private TaxReminderKind kind;
    }
}
