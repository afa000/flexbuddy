package com.angel.flexbuddy.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

/** Minimal outbox acknowledgement; no block snapshot, bearer token or request payload is retained. */
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
    // Deliberately not a foreign key: a retry after permanent deletion must never recreate the block.
    @Column(name = "shift_id", nullable = false)
    private Long shiftId;
}
