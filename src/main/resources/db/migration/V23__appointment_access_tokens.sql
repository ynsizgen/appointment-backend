ALTER TABLE appointment.appointments
    ADD COLUMN access_token_hash varchar(64),
    ADD COLUMN access_token_expires_at timestamptz,
    ADD CONSTRAINT appointments_access_token_hash_unique UNIQUE (access_token_hash);

-- Existing appointments have no issued access token. Keep their hash NULL.
UPDATE appointment.appointments
SET access_token_expires_at = starts_at + INTERVAL '720 hours';

ALTER TABLE appointment.appointments
    ALTER COLUMN access_token_expires_at SET NOT NULL;
