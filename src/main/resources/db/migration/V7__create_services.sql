CREATE TABLE appointment.services (
id uuid PRIMARY KEY,
branch_id uuid NOT NULL REFERENCES appointment.brands(id),
display_name varchar(200) NOT NULL,
duration_minutes integer NOT NULL
CHECK (duration_minutes > 0)
);

CREATE INDEX idx_services_brand_id
    ON appointment.services (brand_id);