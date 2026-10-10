package com.angel.flexbuddy.model;

import java.time.Instant;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One single-use recovery code for a driver who lost their phone or cannot get email. Only a SHA-256 hash is stored,
 * never the code; the codes are long and random, so a plain hash is enough.
 */
@Entity
@Table(name = "recovery_code")
@Getter
@Setter
@NoArgsConstructor
public class RecoveryCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private AppUser owner;

    @Column(name = "code_hash", length = 64, nullable = false)
    private String codeHash;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** When the code was used; null while it can still be used. */
    private Instant usedAt;
}
