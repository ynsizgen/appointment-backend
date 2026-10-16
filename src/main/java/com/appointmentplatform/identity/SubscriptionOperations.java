package com.appointmentplatform.identity;

import java.util.UUID;

public interface SubscriptionOperations {
    void requireActiveSubscription(UUID brandId);
}
