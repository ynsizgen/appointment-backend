CREATE TABLE appointment.employee_services (
    id uuid PRIMARY KEY,
    employee_id uuid NOT NULL REFERENCES appointment.employees(id),
    service_id uuid NOT NULL REFERENCES appointment.services(id),
    CONSTRAINT uq_employee_services_pair UNIQUE (employee_id, service_id)
);

CREATE INDEX idx_employee_services_service_id ON appointment.employee_services (service_id);
