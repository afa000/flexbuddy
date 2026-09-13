package com.angel.flexbuddy.model;

import java.io.Serializable;
import java.time.Instant;

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

@Entity
@Table(name = "reminder_log")
@IdClass(ReminderLog.Key.class)
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ReminderLog {

    @Id
    private Long shiftId;

    @Id
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ReminderKind kind;

    @Column(nullable = false)
    private Instant sentAt;

    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    @EqualsAndHashCode
    public static class Key implements Serializable {
        private Long shiftId;
        private ReminderKind kind;
    }
}
