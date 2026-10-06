package com.appointmentplatform.audit;

import java.util.UUID;

/** Public module API. No persistence types leave the audit module. */
public interface Audit {
    void record(UUID actor, UUID brand, String action, String outcome);
    void failure(UUID actor, String action);
}
