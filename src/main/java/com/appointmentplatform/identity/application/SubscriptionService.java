package com.appointmentplatform.identity.application;

import com.appointmentplatform.identity.SubscriptionOperations;
import com.appointmentplatform.identity.domain.Role;
import com.appointmentplatform.identity.persistence.UserAccountRepository;
import com.appointmentplatform.shared.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class SubscriptionService implements SubscriptionOperations {
    private final UserAccountRepository users;

    public SubscriptionService(UserAccountRepository users) {
        this.users = users;
    }

    @Override
    public void requireActiveSubscription(UUID brandId) {
        if (!users.existsByBrandIdAndRoleAndSubscriptionActiveTrue(brandId, Role.BRAND)) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "İşletmenin aboneliği aktif olmadığı için yeni randevu oluşturulamıyor.");
        }
    }
}
