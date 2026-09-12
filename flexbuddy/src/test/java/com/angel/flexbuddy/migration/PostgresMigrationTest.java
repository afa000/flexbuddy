package com.angel.flexbuddy.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.tool.schema.internal.ExceptionHandlerHaltImpl;
import org.hibernate.tool.schema.spi.ContributableMatcher;
import org.hibernate.tool.schema.spi.ExceptionHandler;
import org.hibernate.tool.schema.spi.ExecutionOptions;
import org.hibernate.tool.schema.spi.SchemaManagementTool;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.model.Expense;

@Tag("postgres")
@EnabledIfEnvironmentVariable(named = "FLEXBUDDY_TEST_POSTGRES_URL", matches = "jdbc:postgresql://.+")
class PostgresMigrationTest {

    private static final String URL = System.getenv("FLEXBUDDY_TEST_POSTGRES_URL");
    private static final String USER = environmentOrDefault("FLEXBUDDY_TEST_POSTGRES_USER", "postgres");
    private static final String PASSWORD = environmentOrDefault("FLEXBUDDY_TEST_POSTGRES_PASSWORD", "postgres");

    @Test
    void v5AddsDriverExpensesWithoutBreakingExistingShiftData() throws Exception {
        String schema = "flexbuddy_migration_test_" + UUID.randomUUID().toString().replace("-", "");
        Flyway throughV3 = flyway(schema, MigrationVersion.fromVersion("3"));
        Flyway latest = flyway(schema, MigrationVersion.LATEST);

        try {
            throughV3.migrate();
            insertLegacyRow(schema);

            assertThat(latest.migrate().migrationsExecuted).isEqualTo(2);
            assertBackfilledValues(schema);
            assertRequiredColumns(schema);
            assertDriverExpenseSchema(schema);
            assertHibernateMappingsMatch(schema);
        } finally {
            latest.clean();
            try (Connection connection = connection(); Statement statement = connection.createStatement()) {
                statement.execute("drop schema if exists " + schema + " cascade");
            }
        }
    }

    private void assertDriverExpenseSchema(String schema) throws Exception {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement("""
                select count(*) from information_schema.columns
                where table_schema = ? and ((table_name = 'shift' and column_name = 'miles')
                  or (table_name = 'app_users' and column_name in ('mileage_rate','vehicle_cost_method'))
                  or (table_name = 'expense' and column_name in ('id','owner_id','shift_id','date','category','amount','note','created_at','updated_at','deleted_at','delete_batch')))
                """)) {
            statement.setString(1, schema);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(14);
            }
        }
    }

    private Flyway flyway(String schema, MigrationVersion target) {
        return Flyway.configure()
                .dataSource(URL, USER, PASSWORD)
                .defaultSchema(schema)
                .schemas(schema)
                .createSchemas(true)
                .cleanDisabled(false)
                .target(target)
                .load();
    }

    private void insertLegacyRow(String schema) throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("set search_path to " + schema);
            try (ResultSet result = statement.executeQuery("""
                    insert into app_users (created_at, display_name, email, password_hash)
                    values (timestamp with time zone '2026-09-01 12:00:00+00', 'Migration test',
                            'migration@example.com', 'hash')
                    returning id
                    """)) {
                assertThat(result.next()).isTrue();
                long ownerId = result.getLong(1);
                try (PreparedStatement insert = connection.prepareStatement("""
                        insert into shift (owner_id, created_at, updated_at, station, date, start_time,
                                           end_time, base_pay, tips)
                        values (?, timestamp with time zone '2026-09-01 12:00:00+00',
                                timestamp with time zone '2026-09-01 12:00:00+00', null, null, null,
                                null, null, null)
                        """)) {
                    insert.setLong(1, ownerId);
                    assertThat(insert.executeUpdate()).isEqualTo(1);
                }
            }
        }
    }

    private void assertBackfilledValues(String schema) throws Exception {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("set search_path to " + schema);
            try (ResultSet result = statement.executeQuery("""
                    select station, date, start_time, end_time, base_pay, tips from shift
                    """)) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString("station")).isEqualTo("Unknown station");
                assertThat(result.getObject("date", LocalDate.class)).isEqualTo(LocalDate.of(2026, 9, 1));
                assertThat(result.getObject("start_time", LocalTime.class)).isEqualTo(LocalTime.MIDNIGHT);
                assertThat(result.getObject("end_time", LocalTime.class)).isEqualTo(LocalTime.MIDNIGHT);
                assertThat(result.getBigDecimal("base_pay")).isEqualByComparingTo(BigDecimal.ZERO);
                assertThat(result.getBigDecimal("tips")).isEqualByComparingTo(BigDecimal.ZERO);
            }
        }
    }

    private void assertRequiredColumns(String schema) throws Exception {
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement("""
                select count(*)
                from information_schema.columns
                where table_schema = ? and table_name = 'shift'
                  and column_name in ('station', 'date', 'start_time', 'end_time', 'base_pay', 'tips')
                  and is_nullable = 'NO'
                """)) {
            statement.setString(1, schema);
            try (ResultSet result = statement.executeQuery()) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(6);
            }
        }
    }

    private void assertHibernateMappingsMatch(String schema) {
        Map<String, Object> settings = Map.of(
                "jakarta.persistence.jdbc.url", URL,
                "jakarta.persistence.jdbc.user", USER,
                "jakarta.persistence.jdbc.password", PASSWORD,
                "hibernate.default_schema", schema,
                "hibernate.physical_naming_strategy",
                "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"
        );
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder().applySettings(settings).build();
        try {
            Metadata metadata = new MetadataSources(registry)
                    .addAnnotatedClasses(AppUser.class, Shift.class, Expense.class)
                    .buildMetadata();
            ExecutionOptions options = new ExecutionOptions() {
                @Override public Map<String, Object> getConfigurationValues() { return settings; }
                @Override public boolean shouldManageNamespaces() { return false; }
                @Override public ExceptionHandler getExceptionHandler() { return ExceptionHandlerHaltImpl.INSTANCE; }
            };
            registry.requireService(SchemaManagementTool.class)
                    .getSchemaValidator(settings)
                    .doValidation(metadata, options, ContributableMatcher.ALL);
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    private static String environmentOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
