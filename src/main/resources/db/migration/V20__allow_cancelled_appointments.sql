ALTER TABLE appointment.appointments
    DROP CONSTRAINT appointments_status_check;

ALTER TABLE appointment.appointments
    ADD CONSTRAINT appointments_status_check
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED'));

-- V17 blocks only PENDING and APPROVED. Cancelled records remain stored.
