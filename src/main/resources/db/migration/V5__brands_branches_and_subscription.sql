-- The old platform approval is NOT evidence of a paid subscription.
-- Retain it for historical reference, but do not use it for authorization.
ALTER TABLE appointment.tenants RENAME TO brands;
ALTER TABLE appointment.brands RENAME COLUMN active TO legacy_platform_active;
ALTER TABLE appointment.users RENAME COLUMN tenant_id TO brand_id;
ALTER TABLE appointment.audit_events RENAME COLUMN tenant_id TO brand_id;
ALTER INDEX appointment.audit_events_tenant_time_idx RENAME TO audit_events_brand_time_idx;

-- Preserve a manually introduced subscription column, if present, without
-- requiring development databases to be reset.
ALTER TABLE appointment.users ADD COLUMN IF NOT EXISTS subscription_active boolean NOT NULL DEFAULT false;

CREATE TABLE appointment.branches (
    id uuid PRIMARY KEY,
    brand_id uuid NOT NULL REFERENCES appointment.brands(id),
    name varchar(200) NOT NULL,
    address varchar(1000) NOT NULL,
    google_maps_url varchar(2048),
    accepting_appointments boolean NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX branches_brand_idx ON appointment.branches(brand_id);
INSERT INTO appointment.branches (id, brand_id, name, address, google_maps_url, created_at)
SELECT gen_random_uuid(), id, 'Merkez', address, google_maps_url, created_at
FROM appointment.brands;
ALTER TABLE appointment.brands RENAME COLUMN address TO legacy_address;
ALTER TABLE appointment.brands ALTER COLUMN legacy_address DROP NOT NULL;
ALTER TABLE appointment.brands RENAME COLUMN google_maps_url TO legacy_google_maps_url;

ALTER TABLE appointment.users ADD COLUMN sessions_revoked_at timestamptz;
