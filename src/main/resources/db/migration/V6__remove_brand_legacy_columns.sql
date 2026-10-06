-- V5 already copied address and maps data into the initial branch.
-- The old platform approval has no meaning in the subscription model.
ALTER TABLE appointment.brands
    DROP COLUMN legacy_address,
    DROP COLUMN legacy_google_maps_url,
    DROP COLUMN legacy_platform_active;
