-- Time-only rows contain no appointment date. Never invent a date during conversion.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM appointment.appointments) THEN
        RAISE EXCEPTION 'Appointment dates must be recovered before converting existing time-only appointments';
    END IF;
END $$;

ALTER TABLE appointment.appointments
    ALTER COLUMN starts_at TYPE timestamptz USING NULL::timestamptz,
    ALTER COLUMN ends_at TYPE timestamptz USING NULL::timestamptz;

ALTER TABLE appointment.appointments DROP CONSTRAINT appointments_status_check;
ALTER TABLE appointment.appointments
    ALTER COLUMN status TYPE varchar(255)
        USING CASE status WHEN 0 THEN 'PENDING' WHEN 1 THEN 'APPROVED' END,
    ALTER COLUMN source SET NOT NULL,
    ADD CONSTRAINT appointments_status_check CHECK (status IN ('PENDING', 'APPROVED'));
