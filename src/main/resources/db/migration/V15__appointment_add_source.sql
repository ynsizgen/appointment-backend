ALTER TABLE appointment.appointments
    ADD COLUMN source varchar(20)
        CHECK (source IN ('BRAND', 'CUSTOMER'));