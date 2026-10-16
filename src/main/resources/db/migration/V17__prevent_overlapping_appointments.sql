CREATE EXTENSION IF NOT EXISTS btree_gist;

-- [start, end): a booking may start exactly when the previous one ends.
-- Existing conflicts cause migration failure; never silently delete appointments.
ALTER TABLE appointment.appointments
    ADD CONSTRAINT ex_appointments_employee_time
    EXCLUDE USING gist (
        employee_id WITH =,
        tstzrange(starts_at, ends_at, '[)') WITH &&
    ) WHERE (status IN ('PENDING', 'APPROVED'));
