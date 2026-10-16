CREATE TABLE appointment.phone_verifications (
    phone_hash varchar(64) PRIMARY KEY,
    code_hash varchar(64),
    expires_at timestamptz,
    sent_at timestamptz,
    attempts integer NOT NULL DEFAULT 0 CHECK (attempts BETWEEN 0 AND 3),
    proof_hash varchar(64),
    proof_expires_at timestamptz
);

CREATE TABLE appointment.phone_verification_sends (
    phone_hash varchar(64) NOT NULL REFERENCES appointment.phone_verifications(phone_hash) ON DELETE CASCADE,
    sent_at timestamptz NOT NULL
);
CREATE INDEX idx_phone_verification_sends_phone ON appointment.phone_verification_sends(phone_hash);
