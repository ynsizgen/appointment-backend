package com.appointmentplatform.brand;

import java.util.UUID;

public interface BrandOperations {
    int cancellationNoticeHours(UUID brandId);

    UUID register(BrandRegistration registration);
}
