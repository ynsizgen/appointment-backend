ALTER TABLE appointment.brands ADD COLUMN slug varchar(120);

-- Existing shared links must not depend on future display-name changes.
UPDATE appointment.brands
SET slug = 'brand-' || replace(id::text, '-', '');

ALTER TABLE appointment.brands
    ALTER COLUMN slug SET NOT NULL,
    ADD CONSTRAINT brands_slug_unique UNIQUE (slug),
    ADD CONSTRAINT brands_slug_format CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$');
