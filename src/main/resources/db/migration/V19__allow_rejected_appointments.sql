ALTER TABLE appointment.appointments
    DROP CONSTRAINT appointments_status_check;

ALTER TABLE appointment.appointments
    ADD CONSTRAINT appointments_status_check
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'));

-- The V17 exclusion constraint still blocks only PENDING and APPROVED rows.
-- REJECTED appointments remain stored but no longer occupy the time range.
