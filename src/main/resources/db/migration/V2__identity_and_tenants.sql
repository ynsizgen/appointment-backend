CREATE TABLE appointment.tenants (
    id uuid PRIMARY KEY,
    display_name varchar(200) NOT NULL,
    address varchar(1000) NOT NULL,
    google_maps_url varchar(2048),
    active boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE appointment.users (
    id uuid PRIMARY KEY,
    email varchar(254) NOT NULL UNIQUE CHECK (email = lower(email)),
    password_hash varchar(512) NOT NULL,
    first_name varchar(100) NOT NULL,
    last_name varchar(100) NOT NULL,
    phone varchar(32),
    role varchar(16) NOT NULL CHECK (role IN ('ADMIN', 'BRAND')),
    tenant_id uuid UNIQUE REFERENCES appointment.tenants(id),
    enabled boolean NOT NULL DEFAULT true,
    mfa_secret text,
    last_mfa_step bigint NOT NULL DEFAULT -1,
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK ((role = 'BRAND' AND tenant_id IS NOT NULL AND mfa_secret IS NULL)
        OR (role = 'ADMIN' AND tenant_id IS NULL AND mfa_secret IS NOT NULL))
);
CREATE TABLE appointment.pending_registrations (
    id uuid PRIMARY KEY,
    email varchar(254) NOT NULL UNIQUE,
    password_hash varchar(512) NOT NULL,
    first_name varchar(100) NOT NULL,
    last_name varchar(100) NOT NULL,
    phone varchar(32) NOT NULL,
    display_name varchar(200) NOT NULL,
    address varchar(1000) NOT NULL,
    google_maps_url varchar(2048),
    token_hash char(64) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE appointment.email_jobs (
    id uuid PRIMARY KEY,
    registration_id uuid NOT NULL UNIQUE REFERENCES appointment.pending_registrations(id) ON DELETE CASCADE,
    encrypted_body text,
    state varchar(16) NOT NULL DEFAULT 'PENDING' CHECK (state IN ('PENDING','SENT','FAILED')),
    created_at timestamptz NOT NULL DEFAULT now(),
    completed_at timestamptz
);
CREATE INDEX email_jobs_pending_idx ON appointment.email_jobs(created_at) WHERE state = 'PENDING';
CREATE TABLE appointment.audit_events (
    id uuid PRIMARY KEY,
    actor_id uuid,
    tenant_id uuid,
    action varchar(64) NOT NULL,
    outcome varchar(16) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX audit_events_tenant_time_idx ON appointment.audit_events(tenant_id, created_at);
CREATE TABLE appointment.rate_limits (
    bucket_key char(64) NOT NULL,
    window_start bigint NOT NULL,
    attempts integer NOT NULL,
    PRIMARY KEY (bucket_key, window_start)
);
