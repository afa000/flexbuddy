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
    /** Flex pays twice a week in most markets, for blocks up to the day before. */
    public static final String DEFAULT_PAYOUT_DAYS = "TUESDAY,FRIDAY";
    public static final int DEFAULT_PAYOUT_LAG_DAYS = 1;
    public static final int MAX_PAYOUT_LAG_DAYS = 14;

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

    /** False only for a new sign-up whose emailed code has not been entered yet. */
    @Column(nullable = false)
    private boolean emailVerified = true;

    /** Google's stable account ID once Google sign-in is linked; null otherwise. */
    @Column(length = 255, unique = true)
    private String googleSubject;

    /** False for an account created with Google until the driver chooses a password. */
    @Column(nullable = false)
    private boolean passwordSet = true;

    /** How the second sign-in code is received; null while two-step sign-in is off. */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private TwoFactorMethod twoFactorMethod;

    /** The authenticator-app secret, encrypted by the app and never stored or logged in plain text. */
    @Column(length = 255)
    private String totpSecret;

    /** The last time step an app code was accepted for, so one code cannot be used twice. */
    private Long totpLastStep;

    /** When the driver dismissed Home's setup card; null while it should still show. Screen state, so it is not backed up. */
    private Instant setupDismissedAt;

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

    /** Show recent blocks without miles on Home and in its Needs attention card. */
    @Column(nullable = false)
    private boolean askMissingMiles = true;

    /** Push a reminder a week before and on each estimated tax due date. */
    @Column(nullable = false)
    private boolean remindTax;

    /** "en" or "es"; null means follow the device's language. */
    @Column(length = 10)
    private String language;

    @Column(nullable = false)
    private int forfeitCutoffMinutes = DEFAULT_FORFEIT_CUTOFF_MINUTES;

    @Column(precision = 10, scale = 2)
    private java.math.BigDecimal weeklyGoal;

    @Column(precision = 10, scale = 2)
    private java.math.BigDecimal monthlyGoal;

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(nullable = false, length = 10)
    private GoalBasis goalBasis = GoalBasis.GROSS;

    /** Weekdays money arrives, as comma-separated DayOfWeek names, and how many days before a payout its blocks end. */
    @Column(nullable = false, length = 80)
    private String payoutDays = DEFAULT_PAYOUT_DAYS;

    @Column(nullable = false)
    private int payoutLagDays = DEFAULT_PAYOUT_LAG_DAYS;

    /** The share of net earnings the driver chose to put aside for taxes; null until they choose one. */
    @Column(precision = 5, scale = 2)
    private java.math.BigDecimal taxSetAsidePercent;

    public AppUser(String displayName, String email, String passwordHash) {
        this.displayName = displayName;
        this.email = email;
        this.passwordHash = passwordHash;
    }

}
