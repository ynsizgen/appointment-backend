CREATE TABLE appointment.employee_weekly_schedules (
    employee_id uuid PRIMARY KEY REFERENCES appointment.employees(id)
);
CREATE TABLE appointment.employee_weekly_hours (
    employee_id uuid NOT NULL REFERENCES appointment.employee_weekly_schedules(employee_id),
    day_of_week varchar(9) NOT NULL CHECK (day_of_week IN ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY')),
    start_time time NOT NULL,
    end_time time NOT NULL,
    CHECK (start_time < end_time),
    UNIQUE (employee_id, day_of_week, start_time)
);
CREATE TABLE appointment.employee_schedule_overrides (
    id uuid PRIMARY KEY,
    employee_id uuid NOT NULL REFERENCES appointment.employees(id),
    date date NOT NULL,
    UNIQUE (employee_id, date)
);
CREATE TABLE appointment.employee_override_hours (
    override_id uuid NOT NULL REFERENCES appointment.employee_schedule_overrides(id),
    start_time time NOT NULL,
    end_time time NOT NULL,
    CHECK (start_time < end_time),
    UNIQUE (override_id, start_time)
);
