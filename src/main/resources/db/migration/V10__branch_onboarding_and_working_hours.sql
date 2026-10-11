-- Keep legacy pending-registration address data, but new registrations do not need it.
ALTER TABLE appointment.pending_registrations ALTER COLUMN address DROP NOT NULL;

CREATE TABLE appointment.branch_working_hours (
    id uuid PRIMARY KEY,
    branch_id uuid NOT NULL REFERENCES appointment.branches(id),
    day_of_week varchar(9) NOT NULL CHECK (day_of_week IN
        ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY')),
    start_time time NOT NULL,
    end_time time NOT NULL,
    CONSTRAINT ck_branch_working_hours_interval CHECK (start_time < end_time),
    CONSTRAINT uq_branch_working_hours UNIQUE (branch_id, day_of_week, start_time)
);
-- Existing branches intentionally receive no invented schedule. Owners configure their hours.
