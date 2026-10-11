CREATE TABLE appointment.branch_schedule_overrides (
    id uuid PRIMARY KEY,
    branch_id uuid NOT NULL REFERENCES appointment.branches(id),
    date date NOT NULL,
    UNIQUE (branch_id, date)
);
CREATE TABLE appointment.branch_override_hours (
    override_id uuid NOT NULL REFERENCES appointment.branch_schedule_overrides(id),
    start_time time NOT NULL,
    end_time time NOT NULL,
    CHECK (start_time < end_time),
    UNIQUE (override_id, start_time)
);
