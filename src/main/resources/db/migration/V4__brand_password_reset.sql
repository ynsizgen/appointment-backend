CREATE TABLE appointment.pending_password_resets (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL UNIQUE REFERENCES appointment.users(id) ON DELETE CASCADE,
    token_hash varchar(64) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

ALTER TABLE appointment.users ADD COLUMN password_changed_at timestamptz;

ALTER TABLE appointment.email_jobs ALTER COLUMN registration_id DROP NOT NULL;
ALTER TABLE appointment.email_jobs ADD COLUMN password_reset_id uuid UNIQUE
    REFERENCES appointment.pending_password_resets(id) ON DELETE CASCADE;
ALTER TABLE appointment.email_jobs ADD CONSTRAINT email_jobs_single_source
    CHECK ((registration_id IS NOT NULL AND password_reset_id IS NULL)
        OR (registration_id IS NULL AND password_reset_id IS NOT NULL));
