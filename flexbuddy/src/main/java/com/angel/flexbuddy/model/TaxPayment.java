package com.angel.flexbuddy.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An estimated tax payment the driver recorded, counted against the reserve for its tax year. */
@Entity
@Table(name = "tax_payment", indexes = @Index(name = "idx_tax_payment_owner_year", columnList = "owner_id,tax_year"))
@Getter
@Setter
@NoArgsConstructor
public class TaxPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    @Column(nullable = false)
    private int taxYear;

    /** Which estimated-payment quarter it was for, 1 to 4, or null when the driver did not say. */
    private Integer quarter;

    @Column(nullable = false)
    private LocalDate paidOn;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(length = 255)
    private String note;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
