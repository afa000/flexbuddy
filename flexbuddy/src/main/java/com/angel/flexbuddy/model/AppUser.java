package com.angel.flexbuddy.model;

import java.time.Instant;
import java.math.BigDecimal;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "app_users")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
public class AppUser {

    public static final String DEFAULT_TIME_ZONE = "America/New_York";
    public static final Set<Integer> REMINDER_LEAD_MINUTES = Set.of(30, 60, 120, 720);
    /** Flex's forfeit window in most markets; drivers can change it for theirs. */
    public static final int DEFAULT_FORFEIT_CUTOFF_MINUTES = 45;
    public static final int MAX_FORFEIT_CUTOFF_MINUTES = 720;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String displayName;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, updatable = false)
    @CreatedDate
    private Instant createdAt;

    private Instant lastBackupAt;

    @Column(precision = 6, scale = 3)
    private BigDecimal mileageRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VehicleCostMethod vehicleCostMethod = VehicleCostMethod.STANDARD_MILEAGE;

    @Column(nullable = false, length = 64)
    private String timeZone = DEFAULT_TIME_ZONE;

    @Column(length = 64, unique = true)
    private String calendarToken;

    private Integer remindBeforeMinutes;

    @Column(nullable = false)
    private boolean remindConfirm;

    /** Push a reminder to log miles shortly after a block ends without any. */
    @Column(nullable = false)
    private boolean remindMiles;

    @Column(nullable = false)
    private int forfeitCutoffMinutes = DEFAULT_FORFEIT_CUTOFF_MINUTES;

    @Column(precision = 10, scale = 2)
    private java.math.BigDecimal weeklyGoal;

    @Column(precision = 10, scale = 2)
    private java.math.BigDecimal monthlyGoal;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false, length = 10)
    private GoalBasis goalBasis = GoalBasis.GROSS;

    public AppUser(String displayName, String email, String passwordHash) {
        this.displayName = displayName;
        this.email = email;
        this.passwordHash = passwordHash;
    }

}
