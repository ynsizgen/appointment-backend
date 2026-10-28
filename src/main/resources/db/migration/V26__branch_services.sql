-- A service remains in the brand catalog; branches explicitly select services.
ALTER TABLE appointment.branches
    ADD CONSTRAINT uq_branches_id_brand UNIQUE (id, brand_id);

ALTER TABLE appointment.services
    ADD CONSTRAINT uq_services_id_brand UNIQUE (id, brand_id);

CREATE TABLE appointment.branch_services (
    branch_id uuid NOT NULL,
    service_id uuid NOT NULL,
    brand_id uuid NOT NULL,
    PRIMARY KEY (branch_id, service_id),
    CONSTRAINT fk_branch_services_branch FOREIGN KEY (branch_id, brand_id)
        REFERENCES appointment.branches (id, brand_id),
    CONSTRAINT fk_branch_services_service FOREIGN KEY (service_id, brand_id)
        REFERENCES appointment.services (id, brand_id)
);

CREATE INDEX idx_branch_services_service ON appointment.branch_services (service_id);
