package com.appointmentplatform;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.assertThat;

class BrandBranchMigrationTests {
    @Test
    void upgradePreservesExistingIdentitiesAddressesAndDoesNotInventPaidEntitlements() throws Exception {
        try (var database = new PostgreSQLContainer("postgres:17")) {
            database.start();
            var old = Flyway.configure().dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                    .schemas("appointment").target("4").load();
            old.migrate();
            sql(database, """
                INSERT INTO appointment.tenants(id, display_name, address, google_maps_url, active) VALUES
                ('00000000-0000-0000-0000-000000000001', 'Existing Brand', 'Original address', 'https://maps.google.com/old', true),
                ('00000000-0000-0000-0000-000000000002', 'Other Brand', 'Other address', NULL, false);
                INSERT INTO appointment.users(id, email, password_hash, first_name, last_name, role, tenant_id) VALUES
                ('00000000-0000-0000-0000-000000000011', 'existing@example.com', 'hash', 'Existing', 'Owner', 'BRAND', '00000000-0000-0000-0000-000000000001');
                INSERT INTO appointment.audit_events(id, actor_id, tenant_id, action, outcome) VALUES
                ('00000000-0000-0000-0000-000000000021', '00000000-0000-0000-0000-000000000011', '00000000-0000-0000-0000-000000000001', 'TENANT_ACTIVATED', 'SUCCESS');
                """);
            var current = Flyway.configure().dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                    .schemas("appointment").load();
            current.migrate();
            current.validate();
            assertThat(sql(database, "SELECT string_agg(column_name || ':' || data_type || ':' || is_nullable, ',' ORDER BY column_name) FROM information_schema.columns WHERE table_schema='appointment' AND table_name='appointments' AND column_name IN ('starts_at','ends_at','status','source');"))
                    .isEqualTo("ends_at:timestamp with time zone:NO,source:character varying:NO,starts_at:timestamp with time zone:NO,status:character varying:NO");
            assertThat(sql(database, "SELECT count(*) FROM appointment.brands;")).isEqualTo("2");
            assertThat(sql(database, "SELECT count(DISTINCT slug) FROM appointment.brands WHERE slug IS NOT NULL;")).isEqualTo("2");
            assertThat(sql(database, "SELECT slug FROM appointment.brands WHERE id='00000000-0000-0000-0000-000000000001';"))
                    .isEqualTo("brand-00000000000000000000000000000001");
            sql(database, "UPDATE appointment.brands SET display_name='Renamed' WHERE id='00000000-0000-0000-0000-000000000002';");
            assertThat(sql(database, "SELECT slug FROM appointment.brands WHERE id='00000000-0000-0000-0000-000000000002';"))
                    .isEqualTo("brand-00000000000000000000000000000002");
            assertThat(sql(database, "SELECT count(*) FROM appointment.branches;")).isEqualTo("2");
            assertThat(sql(database, """
                SELECT b.display_name || '|' || br.name || '|' || br.address || '|' || br.google_maps_url
                FROM appointment.brands b JOIN appointment.branches br ON br.brand_id=b.id
                WHERE b.id='00000000-0000-0000-0000-000000000001';
                """)).isEqualTo("Existing Brand|Merkez|Original address|https://maps.google.com/old");
            assertThat(sql(database, "SELECT enabled || '|' || subscription_active || '|' || brand_id FROM appointment.users;"))
                    .isEqualTo("true|false|00000000-0000-0000-0000-000000000001");
            assertThat(sql(database, "SELECT count(*) FROM appointment.branches WHERE accepting_appointments;" )).isEqualTo("0");
            assertThat(sql(database, "SELECT count(*) FROM information_schema.columns WHERE table_schema='appointment' AND table_name='brands' AND column_name IN ('legacy_address', 'legacy_google_maps_url', 'legacy_platform_active');"))
                    .isEqualTo("0");
            assertThat(sql(database, "SELECT brand_id FROM appointment.audit_events;"))
                    .isEqualTo("00000000-0000-0000-0000-000000000001");
            // Branch ownership is one-to-many; adding branches will not require a schema redesign.
            sql(database, "INSERT INTO appointment.branches(id, brand_id, name, address) VALUES (gen_random_uuid(), '00000000-0000-0000-0000-000000000001', 'Second', 'New address');");
            assertThat(sql(database, "SELECT count(*) FROM appointment.branches WHERE brand_id='00000000-0000-0000-0000-000000000001';")).isEqualTo("2");
        }
    }

    @Test
    void emptyLegacyEmployeeSchedulesStayClosedButPartialSchedulesInheritMissingDays() throws Exception {
        try (var database = new PostgreSQLContainer("postgres:17")) {
            database.start();
            Flyway.configure().dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                    .schemas("appointment").target("12").load().migrate();
            sql(database, """
                INSERT INTO appointment.brands(id, display_name) VALUES ('00000000-0000-0000-0000-000000000001', 'Brand');
                INSERT INTO appointment.branches(id,brand_id,name,address) VALUES
                ('00000000-0000-0000-0000-000000000002','00000000-0000-0000-0000-000000000001','Branch','Address');
                INSERT INTO appointment.employees(id,branch_id,first_name,last_name,active) VALUES
                ('00000000-0000-0000-0000-000000000003','00000000-0000-0000-0000-000000000002','Closed','Employee',true),
                ('00000000-0000-0000-0000-000000000004','00000000-0000-0000-0000-000000000002','Partial','Employee',true);
                INSERT INTO appointment.employee_weekly_schedules(employee_id) VALUES
                ('00000000-0000-0000-0000-000000000003'),('00000000-0000-0000-0000-000000000004');
                INSERT INTO appointment.employee_weekly_hours(employee_id,day_of_week,start_time,end_time) VALUES
                ('00000000-0000-0000-0000-000000000004','MONDAY','10:00','12:00');
                """);
            var current = Flyway.configure().dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                    .schemas("appointment").load();
            current.migrate();
            current.validate();
            assertThat(sql(database, "SELECT count(*) FROM appointment.employee_weekly_closed_days WHERE employee_id='00000000-0000-0000-0000-000000000003'"))
                    .isEqualTo("7");
            assertThat(sql(database, "SELECT count(*) FROM appointment.employee_weekly_closed_days WHERE employee_id='00000000-0000-0000-0000-000000000004'"))
                    .isEqualTo("0");
            assertThat(sql(database, "SELECT count(*) FROM appointment.employee_weekly_hours")).isEqualTo("1");
        }
    }

    @Test
    void cancellationPolicyUpgradePreservesBookingsAndSetsZeroHourDeadline() throws Exception {
        try (var database = new PostgreSQLContainer("postgres:17")) {
            database.start();
            Flyway.configure().dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                    .schemas("appointment").target("20").load().migrate();
            sql(database, """
                INSERT INTO appointment.brands(id, display_name)
                VALUES ('00000000-0000-0000-0000-000000000001', 'Brand');
                INSERT INTO appointment.branches(id, brand_id, name, address)
                VALUES ('00000000-0000-0000-0000-000000000002', '00000000-0000-0000-0000-000000000001', 'Branch', 'Address');
                INSERT INTO appointment.employees(id, branch_id, first_name, last_name, active)
                VALUES ('00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000002', 'Ada', 'Lovelace', true);
                INSERT INTO appointment.services(id, brand_id, display_name, duration_minutes)
                VALUES ('00000000-0000-0000-0000-000000000004', '00000000-0000-0000-0000-000000000001', 'Cut', 60);
                INSERT INTO appointment.appointments(id, branch_id, employee_id, service_id, service_name,
                    duration_minutes, customer_first_name, customer_last_name, customer_phone,
                    starts_at, ends_at, status, source, created_at)
                VALUES ('00000000-0000-0000-0000-000000000005', '00000000-0000-0000-0000-000000000002',
                    '00000000-0000-0000-0000-000000000003', '00000000-0000-0000-0000-000000000004',
                    'Cut', 60, 'Ada', 'Lovelace', '+905551112233',
                    '2030-01-01T10:00:00+03:00', '2030-01-01T11:00:00+03:00', 'PENDING', 'CUSTOMER', now());
                """);
            var current = Flyway.configure().dataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword())
                    .schemas("appointment").load();
            current.migrate();
            current.validate();
            assertThat(sql(database, "SELECT cancellation_notice_hours FROM appointment.brands")).isEqualTo("0");
            assertThat(sql(database, "SELECT count(*) FROM appointment.appointments WHERE cancellation_deadline = starts_at AND status = 'PENDING'"))
                    .isEqualTo("1");
            sql(database, "UPDATE appointment.brands SET cancellation_notice_hours = 3");
            assertThat(sql(database, "SELECT count(*) FROM appointment.appointments WHERE cancellation_deadline = starts_at"))
                    .isEqualTo("1");
        }
    }

    private String sql(PostgreSQLContainer database, String query) throws Exception {
        var result = database.execInContainer("psql", "-U", database.getUsername(), "-d", database.getDatabaseName(),
                "-v", "ON_ERROR_STOP=1", "-t", "-A", "-c", query);
        assertThat(result.getExitCode()).as(result.getStderr()).isZero();
        return result.getStdout().strip();
    }
}
