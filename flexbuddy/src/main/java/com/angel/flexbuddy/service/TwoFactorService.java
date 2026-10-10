package com.angel.flexbuddy.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.angel.flexbuddy.exception.InvalidAccountPasswordException;
import com.angel.flexbuddy.exception.InvalidTwoFactorCodeException;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.EmailCodePurpose;
import com.angel.flexbuddy.model.RecoveryCode;
import com.angel.flexbuddy.model.TwoFactorMethod;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.RecoveryCodeRepository;
import com.angel.flexbuddy.security.AttemptLimiter;
import com.angel.flexbuddy.security.Totp;
import com.angel.flexbuddy.security.TwoFactorSecretCipher;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

/**
 * Optional two-step sign-in. A driver chooses an authenticator app or emailed codes, and gets single-use recovery codes
 * either way. Authenticator secrets are encrypted at rest, recovery codes are stored as hashes, and a code that was
 * accepted once is never accepted again. Every wrong code counts toward the same per-email sign-in lock as a wrong
 * password.
 */
@Service
public class TwoFactorService {

    public enum Result { OK, OK_RECOVERY, WRONG, EXPIRED }

    /** What the setup page shows while the driver adds the key to an app. */
    public record AppSetup(String secret, String otpauthUri, String qrSvg) {

        /** The key in groups of four letters, which is easier to type into an app than one long run. */
        public String groupedSecret() {
            return secret.replaceAll("(.{4})(?=.)", "$1 ");
        }
    }

    public static final int RECOVERY_CODE_COUNT = 10;
    private static final String RECOVERY_ALPHABET = "ABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final int RECOVERY_LENGTH = 10;

    private final AppUserRepository userRepository;
    private final RecoveryCodeRepository recoveryRepository;
    private final EmailCodeService emailCodes;
    private final TwoFactorSecretCipher cipher;
    private final PasswordEncoder passwordEncoder;
    private final AttemptLimiter limiter;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public TwoFactorService(AppUserRepository userRepository, RecoveryCodeRepository recoveryRepository,
            EmailCodeService emailCodes, TwoFactorSecretCipher cipher, PasswordEncoder passwordEncoder,
            AttemptLimiter limiter, Clock clock) {
        this.userRepository = userRepository;
        this.recoveryRepository = recoveryRepository;
        this.emailCodes = emailCodes;
        this.cipher = cipher;
        this.passwordEncoder = passwordEncoder;
        this.limiter = limiter;
        this.clock = clock;
    }

    // ---- Setup ---------------------------------------------------------------------------------------------------

    /** A new secret to show as a QR code and a typed key. It is kept in the session, not stored, until confirmed. */
    public AppSetup beginApp(AppUser user) {
        return setupFor(user, Totp.base32(Totp.newSecret()));
    }

    /** The setup details for a secret already chosen, so a wrong first code can be retried without a new key. */
    public AppSetup setupFor(AppUser user, String base32Secret) {
        String uri = Totp.otpauthUri(base32Secret, user.getEmail());
        return new AppSetup(base32Secret, uri, qrSvg(uri));
    }

    /** Turns the app method on once the driver types a code the app made from that secret. Returns the recovery codes. */
    @Transactional
    public List<String> confirmApp(AppUser user, String base32Secret, String code) {
        byte[] key;
        try {
            key = Totp.fromBase32(base32Secret);
        } catch (IllegalArgumentException exception) {
            throw new InvalidTwoFactorCodeException("The setup key is not valid. Start again.");
        }
        OptionalLong step = Totp.matchStep(key, clean(code), nowStep());
        if (step.isEmpty()) {
            throw new InvalidTwoFactorCodeException("That code isn't right. Check your app and try again.");
        }
        user.setTotpSecret(cipher.encrypt(Totp.base32(key)));
        user.setTwoFactorMethod(TwoFactorMethod.APP);
        user.setTotpLastStep(step.getAsLong());
        userRepository.save(user);
        return newRecoveryCodes(user);
    }

    /** Emails a code the driver must type back to prove the mail arrives. */
    public EmailCodeService.SendResult beginEmail(AppUser user, String ip) {
        return emailCodes.send(user, EmailCodePurpose.SIGN_IN, ip);
    }

    /** Turns the email method on once the emailed code is entered. Returns the recovery codes. */
    @Transactional
    public List<String> confirmEmail(AppUser user, String code) {
        EmailCodeService.CheckResult result = emailCodes.check(user, EmailCodePurpose.SIGN_IN, clean(code));
        if (result != EmailCodeService.CheckResult.OK) {
            throw new InvalidTwoFactorCodeException(result == EmailCodeService.CheckResult.EXPIRED
                    ? "That code has expired. Send a new one."
                    : "That code isn't right. Check the email and try again.");
        }
        user.setTwoFactorMethod(TwoFactorMethod.EMAIL);
        user.setTotpSecret(null);
        user.setTotpLastStep(null);
        userRepository.save(user);
        return newRecoveryCodes(user);
    }

    // ---- Recovery codes --------------------------------------------------------------------------------------------

    /** Replaces every recovery code with ten new ones, returned once in plain text and stored only as hashes. */
    @Transactional
    public List<String> newRecoveryCodes(AppUser user) {
        recoveryRepository.deleteAllByOwnerId(user.getId());
        Instant now = clock.instant();
        List<String> shown = new ArrayList<>();
        for (int index = 0; index < RECOVERY_CODE_COUNT; index++) {
            StringBuilder raw = new StringBuilder();
            for (int position = 0; position < RECOVERY_LENGTH; position++) {
                raw.append(RECOVERY_ALPHABET.charAt(random.nextInt(RECOVERY_ALPHABET.length())));
            }
            RecoveryCode stored = new RecoveryCode();
            stored.setOwner(user);
            stored.setCodeHash(sha256(raw.toString()));
            stored.setCreatedAt(now);
            recoveryRepository.save(stored);
            shown.add(raw.substring(0, 5) + "-" + raw.substring(5));
        }
        return shown;
    }

    public long recoveryCodesLeft(AppUser user) {
        return recoveryRepository.countByOwnerIdAndUsedAtIsNull(user.getId());
    }

    // ---- Signing in --------------------------------------------------------------------------------------------------

    /** Emails the sign-in code for the email method. */
    public EmailCodeService.SendResult sendSignInEmail(AppUser user, String ip) {
        return emailCodes.send(user, EmailCodePurpose.SIGN_IN, ip);
    }

    /** Checks a typed code: a recovery code, or one from the driver's authenticator app or inbox. */
    @Transactional
    public Result verifySignIn(AppUser user, String entered) {
        String cleaned = clean(entered).toUpperCase(Locale.ROOT);
        if (cleaned.length() == RECOVERY_LENGTH) {
            Optional<RecoveryCode> match = recoveryRepository.findAllByOwnerIdAndUsedAtIsNull(user.getId()).stream()
                    .filter(code -> MessageDigest.isEqual(code.getCodeHash().getBytes(StandardCharsets.UTF_8),
                            sha256(cleaned).getBytes(StandardCharsets.UTF_8)))
                    .findFirst();
            if (match.isPresent()) {
                match.get().setUsedAt(clock.instant());
                recoveryRepository.save(match.get());
                return Result.OK_RECOVERY;
            }
            return wrong(user);
        }
        if (!cleaned.matches("\\d{6}") || user.getTwoFactorMethod() == null) {
            return wrong(user);
        }
        if (user.getTwoFactorMethod() == TwoFactorMethod.APP) {
            return verifyApp(user, cleaned);
        }
        EmailCodeService.CheckResult result = emailCodes.check(user, EmailCodePurpose.SIGN_IN, cleaned);
        return switch (result) {
            case OK -> Result.OK;
            case EXPIRED -> Result.EXPIRED;
            default -> wrong(user);
        };
    }

    private Result verifyApp(AppUser user, String code) {
        byte[] key;
        try {
            key = Totp.fromBase32(cipher.decrypt(user.getTotpSecret()));
        } catch (RuntimeException exception) {
            // An unreadable secret cannot be matched, whatever the reason.
            return wrong(user);
        }
        OptionalLong step = Totp.matchStep(key, code, nowStep());
        // A step that is not newer than the last accepted one is a replay of a code that was already used.
        if (step.isEmpty() || (user.getTotpLastStep() != null && step.getAsLong() <= user.getTotpLastStep())) {
            return wrong(user);
        }
        user.setTotpLastStep(step.getAsLong());
        userRepository.save(user);
        return Result.OK;
    }

    /** Turns two-step off. It needs the password and a current code, so a stolen session cannot do it. */
    @Transactional
    public void disable(AppUser user, String password, String code) {
        if (!passwordEncoder.matches(password == null ? "" : password, user.getPasswordHash())) {
            throw new InvalidAccountPasswordException();
        }
        requireCode(user, code);
        user.setTwoFactorMethod(null);
        user.setTotpSecret(null);
        user.setTotpLastStep(null);
        userRepository.save(user);
        recoveryRepository.deleteAllByOwnerId(user.getId());
    }

    /** Makes ten new recovery codes, which also cancels the old ones. It needs a current code. */
    @Transactional
    public List<String> regenerateRecoveryCodes(AppUser user, String code) {
        requireCode(user, code);
        return newRecoveryCodes(user);
    }

    private void requireCode(AppUser user, String code) {
        Result result = verifySignIn(user, code);
        if (result == Result.WRONG || result == Result.EXPIRED) {
            throw new InvalidTwoFactorCodeException("That code isn't right.");
        }
    }

    /** A wrong code is a failed sign-in as far as the lock is concerned. */
    private Result wrong(AppUser user) {
        limiter.record(AttemptLimiter.LOGIN_EMAIL, AttemptLimiter.emailKey(user.getEmail()));
        return Result.WRONG;
    }

    // ---- Helpers ----------------------------------------------------------------------------------------------------

    private long nowStep() {
        return clock.instant().getEpochSecond() / Totp.STEP_SECONDS;
    }

    /** Spaces and dashes are how codes are written, not part of them. */
    private static String clean(String entered) {
        return entered == null ? "" : entered.replaceAll("[\\s-]", "");
    }

    private static String sha256(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is always available", exception);
        }
    }

    /** The QR code as a single SVG path, drawn here so the secret never goes to a third-party script. */
    static String qrSvg(String content) {
        try {
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 2);
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, hints);
            StringBuilder path = new StringBuilder();
            for (int y = 0; y < matrix.getHeight(); y++) {
                for (int x = 0; x < matrix.getWidth(); x++) {
                    if (matrix.get(x, y)) {
                        path.append('M').append(x).append(' ').append(y).append("h1v1h-1z");
                    }
                }
            }
            return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + matrix.getWidth() + ' ' + matrix.getHeight()
                    + "\" shape-rendering=\"crispEdges\" role=\"img\" aria-label=\"QR code for your authenticator app\">"
                    + "<path d=\"" + path + "\" fill=\"#000\"/></svg>";
        } catch (WriterException exception) {
            throw new IllegalStateException("The setup address could not be drawn as a QR code.", exception);
        }
    }
}
