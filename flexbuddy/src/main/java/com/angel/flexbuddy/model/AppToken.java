package com.angel.flexbuddy.model;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/** A device credential's hash. The bearer secret is never persisted. */
@Entity
@Table(name = "app_token")
@Getter
@Setter
public class AppToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;
    @Column(name = "token_hash", length = 64, nullable = false, unique = true)
    private String tokenHash;
    @Column(length = 80)
    private String deviceName;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant lastUsedAt;
    @Column(nullable = false)
    private Instant expiresAt;
}
