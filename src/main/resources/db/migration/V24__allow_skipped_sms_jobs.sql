ALTER TABLE appointment.sms_jobs
    DROP CONSTRAINT sms_jobs_status_check;

ALTER TABLE appointment.sms_jobs
    ADD CONSTRAINT sms_jobs_status_check
    CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED'));
