package com.angel.flexbuddy.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/**
 * Minimal outbox acknowledgement; no record snapshot, bearer token or request payload is retained. Exactly one target
 * is set: a block, an expense or a payout date (a database check enforces it).
 */
@Entity
@Table(name = "api_write_receipt", uniqueConstraints =
        @UniqueConstraint(name = "uk_api_write_owner_key", columnNames = {"owner_id", "request_key"}))
@Getter
@Setter
public class ApiWriteReceipt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private AppUser owner;
    @Column(name = "request_key", length = 36, nullable = false)
    private String requestKey;
    @Column(name = "request_hash", length = 64, nullable = false)
    private String requestHash;
    // Deliberately not foreign keys: a retry after permanent deletion must never recreate the record.
    @Column(name = "shift_id")
    private Long shiftId;
    @Column(name = "expense_id")
    private Long expenseId;
    @Column(name = "payout_date")
    private java.time.LocalDate payoutDate;
}
