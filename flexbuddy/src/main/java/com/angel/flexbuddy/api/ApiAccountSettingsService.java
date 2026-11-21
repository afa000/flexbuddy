package com.angel.flexbuddy.api;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.angel.flexbuddy.dto.AccountSettingsRequest;
import com.angel.flexbuddy.dto.AccountSettingsResponse;
import com.angel.flexbuddy.dto.GoalSettingsRequest;
import com.angel.flexbuddy.dto.LanguageRequest;
import com.angel.flexbuddy.dto.PayoutSettingsRequest;
import com.angel.flexbuddy.dto.ReminderSettingsRequest;
import com.angel.flexbuddy.dto.TaxSettingsRequest;
import com.angel.flexbuddy.dto.TimeZoneRequest;
import com.angel.flexbuddy.model.GoalBasis;
import com.angel.flexbuddy.model.VehicleCostMethod;
import com.angel.flexbuddy.service.AccountSettingsService;

import jakarta.validation.Validator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads and changes the driver's settings for the native app (FB-059), one group per write, through the website's
 * own request records and {@link AccountSettingsService}, so every limit is the website's. A write must name the
 * settings it was based on with {@code If-Match}; there is no idempotency receipt, because a group write replaces the
 * whole group and a retry whose values are already saved simply succeeds.
 */
@Service
@ConditionalOnProperty(name = "flexbuddy.api.enabled", havingValue = "true")
public class ApiAccountSettingsService {
    /** The groups under PUT /api/v1/account/settings/{group}. */
    public enum Group {
        LANGUAGE("language"), TIME_ZONE("time-zone"), GOALS("goals"), TAX("tax"), REMINDERS("reminders"),
        MILEAGE("mileage"), PAYOUTS("payouts");

        private final String path;

        Group(String path) { this.path = path; }

        static Group of(String path) {
            for (Group group : values()) if (group.path.equals(path)) return group;
            throw new IllegalArgumentException(path);
        }
    }

    /** The settings changed since the phone read them; {@code current} is what is saved now. */
    public static class Conflict extends RuntimeException {
        private final transient ApiAccountSettings current;
        Conflict(ApiAccountSettings current) { this.current = current; }
        ApiAccountSettings current() { return current; }
    }

    /** A body that isn't a JSON object at all. */
    public static class Unreadable extends RuntimeException { }

    private static final String STRONG_TAG = "\"[0-9a-f]{64}\"";
    private static final String DECIMAL = "-?[0-9]+(\\.[0-9]+)?";
    /** Website validation paths that the API names differently. */
    private static final Map<String, String> FIELD_NAMES = Map.of(
            "mileageRate", "customMileageRate",
            "remindBeforeMinutesSupported", "remindBeforeMinutes");

    private final AccountSettingsService settings;
    private final ApiWriteReceipts receipts;
    private final Validator validator;
    private final ObjectMapper mapper;

    public ApiAccountSettingsService(AccountSettingsService settings, ApiWriteReceipts receipts, Validator validator,
            ObjectMapper mapper) {
        this.settings = settings;
        this.receipts = receipts;
        this.validator = validator;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public ApiAccountSettings get(String email) {
        return ApiAccountSettings.of(settings.get(email));
    }

    @Transactional
    public ApiAccountSettings write(String email, Group group, JsonNode body, String ifMatch) {
        String expected = ifMatch == null ? null : ifMatch.trim();
        if (expected == null || !expected.matches(STRONG_TAG)) {
            throw new ApiWriteReceipts.InvalidWrite(Map.of("If-Match", "invalid"));
        }
        if (body == null || !body.isObject()) throw new Unreadable();
        // One account lock serializes this account's API writes, so the check and the save see the same settings.
        receipts.lock(email);
        AccountSettingsResponse current = settings.get(email);
        Object request = request(group, body, current);
        ApiAccountSettings saved = ApiAccountSettings.of(current);
        if (!ApiEtags.tag(mapper, saved).equals(expected)) {
            if (alreadySaved(group, request, current)) return saved;
            throw new Conflict(saved);
        }
        return ApiAccountSettings.of(apply(email, group, request));
    }

    /** The website's request for this group, valid by its own rules, or {@link ApiWriteReceipts.InvalidWrite}. */
    private Object request(Group group, JsonNode body, AccountSettingsResponse current) {
        Fields in = new Fields(body);
        Object request = switch (group) {
            case LANGUAGE -> new LanguageRequest(in.text("language", true));
            case TIME_ZONE -> new TimeZoneRequest(in.text("timeZone", false));
            case GOALS -> new GoalSettingsRequest(in.decimal("weeklyGoal", true), in.decimal("monthlyGoal", true),
                    in.code("goalBasis", GoalBasis.class));
            case TAX -> new TaxSettingsRequest(in.decimal("taxSetAsidePercent", true), in.bool("remindTax"));
            // The saved time zone goes back unchanged; the API changes it only through /time-zone.
            case REMINDERS -> new ReminderSettingsRequest(current.timeZone(), in.integer("remindBeforeMinutes", true),
                    Boolean.TRUE.equals(in.bool("remindConfirm")), in.bool("remindMiles"),
                    in.integer("forfeitCutoffMinutes", false), in.bool("askMissingMiles"));
            case MILEAGE -> new AccountSettingsRequest(in.code("vehicleCostMethod", VehicleCostMethod.class),
                    in.decimal("customMileageRate", true));
            case PAYOUTS -> new PayoutSettingsRequest(in.days("payoutDays"), in.integerOrZero("payoutLagDays"));
        };
        Map<String, String> fields = in.errors;
        for (var violation : validator.validate(request)) {
            String path = violation.getPropertyPath().toString();
            fields.putIfAbsent(FIELD_NAMES.getOrDefault(path, path), "invalid");
        }
        if (!fields.isEmpty()) throw new ApiWriteReceipts.InvalidWrite(fields);
        return normalized(request);
    }

    /**
     * Decimals at the column's scale, so the settings answered right after the save are byte for byte what a later
     * read from the database answers, and the tag the phone keeps stays valid. Validation has already limited the
     * number of decimals, so nothing is rounded.
     */
    private static Object normalized(Object request) {
        return switch (request) {
            case GoalSettingsRequest goals -> new GoalSettingsRequest(scale(goals.weeklyGoal(), 2),
                    scale(goals.monthlyGoal(), 2), goals.goalBasis());
            case TaxSettingsRequest tax -> new TaxSettingsRequest(scale(tax.taxSetAsidePercent(), 2), tax.remindTax());
            case AccountSettingsRequest mileage -> new AccountSettingsRequest(mileage.vehicleCostMethod(),
                    scale(mileage.mileageRate(), 3));
            default -> request;
        };
    }

    private static BigDecimal scale(BigDecimal value, int scale) {
        return value == null ? null : value.setScale(scale);
    }

    private AccountSettingsResponse apply(String email, Group group, Object request) {
        return switch (group) {
            case LANGUAGE -> settings.updateLanguage(email, ((LanguageRequest) request).language());
            case TIME_ZONE -> settings.updateTimeZone(email, (TimeZoneRequest) request);
            case GOALS -> settings.updateGoals(email, (GoalSettingsRequest) request);
            case TAX -> settings.updateTax(email, (TaxSettingsRequest) request);
            case REMINDERS -> settings.updateReminders(email, (ReminderSettingsRequest) request);
            case MILEAGE -> settings.update(email, (AccountSettingsRequest) request);
            case PAYOUTS -> settings.updatePayouts(email, (PayoutSettingsRequest) request);
        };
    }

    /** Whether saving {@code request} would change nothing: a retry of a write whose answer was lost. */
    private static boolean alreadySaved(Group group, Object request, AccountSettingsResponse current) {
        return switch (group) {
            case LANGUAGE -> Objects.equals(((LanguageRequest) request).language(), current.language());
            case TIME_ZONE -> ((TimeZoneRequest) request).timeZone().equals(current.timeZone());
            case GOALS -> {
                var goals = (GoalSettingsRequest) request;
                yield same(goals.weeklyGoal(), current.weeklyGoal()) && same(goals.monthlyGoal(), current.monthlyGoal())
                        && goals.goalBasis() == current.goalBasis();
            }
            case TAX -> {
                var tax = (TaxSettingsRequest) request;
                yield same(tax.taxSetAsidePercent(), current.taxSetAsidePercent())
                        && tax.remindTax() == current.remindTax();
            }
            case REMINDERS -> {
                var reminders = (ReminderSettingsRequest) request;
                yield Objects.equals(reminders.remindBeforeMinutes(), current.remindBeforeMinutes())
                        && reminders.remindConfirm() == current.remindConfirm()
                        && reminders.remindMiles() == current.remindMiles()
                        && reminders.askMissingMiles() == current.askMissingMiles()
                        && reminders.forfeitCutoffMinutes() == current.forfeitCutoffMinutes();
            }
            case MILEAGE -> {
                var mileage = (AccountSettingsRequest) request;
                boolean sameRate = mileage.mileageRate() == null ? !current.customMileageRate()
                        : current.customMileageRate() && same(mileage.mileageRate(), current.mileageRate());
                yield mileage.vehicleCostMethod() == current.vehicleCostMethod() && sameRate;
            }
            case PAYOUTS -> {
                var payouts = (PayoutSettingsRequest) request;
                yield Set.copyOf(current.payoutDays()).equals(payouts.payoutDays())
                        && payouts.payoutLagDays() == current.payoutLagDays();
            }
        };
    }

    private static boolean same(BigDecimal a, BigDecimal b) {
        return a == null ? b == null : b != null && a.compareTo(b) == 0;
    }

    /** Reads typed fields from the body and records each one that is missing or of the wrong type. */
    private static final class Fields {
        private final JsonNode body;
        private final Map<String, String> errors = new TreeMap<>();

        Fields(JsonNode body) { this.body = body; }

        /** The field, or null when it is absent or null (recording an error when it is required). */
        private JsonNode node(String name, boolean nullable) {
            JsonNode node = body.get(name);
            if (node == null || node.isNull()) {
                if (!nullable) invalid(name);
                return null;
            }
            return node;
        }

        String text(String name, boolean nullable) {
            JsonNode node = node(name, nullable);
            if (node == null) return null;
            if (!node.isString()) return invalid(name);
            return node.asString();
        }

        /** Decimals are strings, as the API returns them; a JSON number is refused. */
        BigDecimal decimal(String name, boolean nullable) {
            String value = text(name, nullable);
            if (value == null) return null;
            if (!value.matches(DECIMAL)) return invalid(name);
            return new BigDecimal(value);
        }

        Integer integer(String name, boolean nullable) {
            JsonNode node = node(name, nullable);
            if (node == null) return null;
            if (!node.isIntegralNumber() || !node.canConvertToInt()) return invalid(name);
            return node.intValue();
        }

        int integerOrZero(String name) {
            Integer value = integer(name, false);
            return value == null ? 0 : value;
        }

        Boolean bool(String name) {
            JsonNode node = node(name, false);
            if (node == null) return null;
            if (!node.isBoolean()) return invalid(name);
            return node.booleanValue();
        }

        /** A lower-case code as the API returns it. */
        <E extends Enum<E>> E code(String name, Class<E> type) {
            String value = text(name, false);
            if (value == null) return null;
            E parsed = parse(value, type);
            return parsed == null ? invalid(name) : parsed;
        }

        /** Lower-case weekday codes in any order; a repeated day is refused. */
        Set<DayOfWeek> days(String name) {
            JsonNode node = node(name, false);
            if (node == null) return null;
            if (!node.isArray()) return invalid(name);
            Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
            for (JsonNode item : node) {
                DayOfWeek day = item.isString() ? parse(item.asString(), DayOfWeek.class) : null;
                if (day == null || !days.add(day)) return invalid(name);
            }
            return days;
        }

        private static <E extends Enum<E>> E parse(String value, Class<E> type) {
            if (!value.equals(value.toLowerCase(Locale.ROOT))) return null;
            try {
                return Enum.valueOf(type, value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException exception) {
                return null;
            }
        }

        private <T> T invalid(String name) {
            errors.put(name, "invalid");
            return null;
        }
    }
}
