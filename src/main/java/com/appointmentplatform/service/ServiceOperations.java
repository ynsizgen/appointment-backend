package com.appointmentplatform.service;
import java.util.UUID;

public interface ServiceOperations {
    void requireOwnedBy(UUID serviceId, UUID brandId);
    ServiceDetails findById(UUID id);}