package com.angel.flexbuddy.model;

import java.time.Instant;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "app_sign_in_challenge")
@Getter
@Setter
public class AppSignInChallenge {
    public enum Purpose { VERIFY_EMAIL, TWO_FACTOR }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;
    @Column(name = "challenge_hash", length = 64, nullable = false, unique = true)
    private String challengeHash;
    @Enumerated(EnumType.STRING) @Column(length = 20, nullable = false)
    private Purpose purpose;
    @Column(length = 80)
    private String deviceName;
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant expiresAt;
}
