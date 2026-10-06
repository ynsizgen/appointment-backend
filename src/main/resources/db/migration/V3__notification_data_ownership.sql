-- Copy the delivery data into the notification-owned record. Workers no longer
-- query the identity module's pending-registration table.
ALTER TABLE appointment.email_jobs ADD COLUMN recipient varchar(254);
ALTER TABLE appointment.email_jobs ADD COLUMN expires_at timestamptz;

UPDATE appointment.email_jobs j
SET recipient = r.email, expires_at = r.expires_at
FROM appointment.pending_registrations r
WHERE r.id = j.registration_id;

ALTER TABLE appointment.email_jobs ALTER COLUMN recipient SET NOT NULL;
ALTER TABLE appointment.email_jobs ALTER COLUMN expires_at SET NOT NULL;

-- The existing foreign key remains: completing/expiring a registration removes
-- its verification job atomically. No cross-module JPA entity association exists.
