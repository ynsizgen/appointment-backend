-- Matches the current Appointment entity. LocalTime stores no appointment date.
-- Date-aware overlap protection requires timestamp fields in a follow-up migration.
CREATE TABLE appointment.appointments (
    id uuid PRIMARY KEY,
    branch_id uuid NOT NULL REFERENCES appointment.branches(id),
    employee_id uuid NOT NULL REFERENCES appointment.employees(id),
    service_id uuid NOT NULL REFERENCES appointment.services(id),
    service_name varchar(255) NOT NULL,
    duration_minutes integer NOT NULL CHECK (duration_minutes > 0),
    customer_first_name varchar(255) NOT NULL,
    customer_last_name varchar(255) NOT NULL,
    customer_phone varchar(255) NOT NULL,
    customer_email varchar(255),
    starts_at time NOT NULL,
    ends_at time NOT NULL,
    -- AppointmentStatus currently uses JPA's default ORDINAL mapping: 0=PENDING, 1=APPROVED.
    status smallint NOT NULL CHECK (status IN (0, 1)),
    created_at timestamptz NOT NULL,
    CONSTRAINT ck_appointments_time_order CHECK (starts_at < ends_at)
);

CREATE INDEX idx_appointments_branch_id ON appointment.appointments(branch_id);
CREATE INDEX idx_appointments_employee_id ON appointment.appointments(employee_id);
CREATE INDEX idx_appointments_service_id ON appointment.appointments(service_id);
