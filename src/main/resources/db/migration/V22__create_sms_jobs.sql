-- Notification owns these jobs; appointment_id is a reference, not a cross-module FK.
CREATE TABLE appointment.sms_jobs (
    id uuid PRIMARY KEY,
    appointment_id uuid NOT NULL,
    recipient varchar(16) NOT NULL,
    encrypted_body text,
    type varchar(32) NOT NULL,
    status varchar(16) NOT NULL DEFAULT 'PENDING',
    attempt_count integer NOT NULL DEFAULT 0 CHECK (attempt_count >= 0),
    next_attempt_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    completed_at timestamptz,
    CONSTRAINT sms_jobs_type_check CHECK (type IN (
        'APPOINTMENT_CREATED', 'REMINDER_24_HOURS', 'REMINDER_2_HOURS',
        'APPOINTMENT_REJECTED', 'APPOINTMENT_CANCELLED'
    )),
    CONSTRAINT sms_jobs_status_check CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    CONSTRAINT sms_jobs_pending_body_check CHECK (status <> 'PENDING' OR encrypted_body IS NOT NULL),
    CONSTRAINT sms_jobs_appointment_type_unique UNIQUE (appointment_id, type)
);

CREATE INDEX idx_sms_jobs_pending_delivery
    ON appointment.sms_jobs(next_attempt_at, created_at, id)
    WHERE status = 'PENDING';
