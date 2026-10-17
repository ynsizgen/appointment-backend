ALTER TABLE appointment.brands
    ADD COLUMN cancellation_notice_hours integer NOT NULL DEFAULT 0,
    ADD CONSTRAINT brands_cancellation_notice_hours_check
        CHECK (cancellation_notice_hours BETWEEN 0 AND 168);

ALTER TABLE appointment.appointments
    ADD COLUMN cancellation_deadline timestamptz;

-- No cancellation policy existed before V21. Preserve the initial zero-hour policy.
UPDATE appointment.appointments
SET cancellation_deadline = starts_at;

ALTER TABLE appointment.appointments
    ALTER COLUMN cancellation_deadline SET NOT NULL,
    ADD CONSTRAINT appointments_cancellation_deadline_check
        CHECK (cancellation_deadline <= starts_at);
