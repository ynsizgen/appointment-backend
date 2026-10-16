CREATE TABLE appointment.employee_weekly_closed_days (
    employee_id uuid NOT NULL REFERENCES appointment.employee_weekly_schedules(employee_id),
    day_of_week varchar(9) NOT NULL CHECK (day_of_week IN ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY')),
    PRIMARY KEY (employee_id, day_of_week)
);
-- Preserve explicitly empty legacy schedules (previously "closed all week").
-- Missing days of non-empty schedules now inherit branch hours.
INSERT INTO appointment.employee_weekly_closed_days(employee_id, day_of_week)
SELECT s.employee_id, d.day_of_week
FROM appointment.employee_weekly_schedules s
CROSS JOIN (VALUES ('MONDAY'),('TUESDAY'),('WEDNESDAY'),('THURSDAY'),('FRIDAY'),('SATURDAY'),('SUNDAY')) d(day_of_week)
WHERE NOT EXISTS (SELECT 1 FROM appointment.employee_weekly_hours h WHERE h.employee_id = s.employee_id);
