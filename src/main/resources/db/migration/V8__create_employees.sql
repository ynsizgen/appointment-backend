CREATE TABLE appointment.employees (
    id uuid PRIMARY KEY,
    branch_id uuid NOT NULL REFERENCES appointment.branches(id),
    first_name varchar(20) NOT NULL,
    last_name varchar(30) NOT NULL,
    active boolean NOT NULL DEFAULT true
);

CREATE INDEX idx_employees_branch_id ON appointment.employees (branch_id);
