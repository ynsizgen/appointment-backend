package com.appointmentplatform.identity.application;
import com.appointmentplatform.identity.domain.*;
import com.appointmentplatform.identity.persistence.UserAccountRepository;
import com.appointmentplatform.audit.Audit;
import com.appointmentplatform.shared.ApiException;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
@Service
@Transactional(readOnly = true)
public class UserAdministrationService {
    private final UserAccountRepository users;
    private final Audit audit;
    private final java.time.Clock clock;

    public UserAdministrationService(UserAccountRepository users, Audit audit, java.time.Clock clock) {
        this.users = users;
        this.audit = audit; this.clock = clock;
    }

    @PreAuthorize("hasRole('ADMIN')")
    public List<AccountDetails> list(int page, int size) {
        if (page < 0 || page > 100000 || size < 1 || size > 100)
            throw new ApiException(HttpStatus.BAD_REQUEST, "Sayfalama değerleri geçersiz.");
        return users.findByRole(Role.BRAND, PageRequest.of(page, size, Sort.by("createdAt", "id")))
                .stream().map(this::details).toList();
    }
    @Transactional
    @PreAuthorize("hasRole('ADMIN') and #actorId == authentication.principal.id")
    public AccountDetails updateStatus(UUID actorId, UUID id, boolean enabled, boolean subscriptionActive) {
        var user = users.findForUpdate(id).filter(u -> u.role() == Role.BRAND)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "İşletme hesabı bulunamadı."));
        if (user.enabled() != enabled)
            audit.record(actorId, user.brandId(), enabled ? "USER_ENABLED" : "USER_DISABLED", "SUCCESS");
        if (user.isSubscriptionActive() != subscriptionActive)
            audit.record(actorId, user.brandId(), subscriptionActive ? "SUBSCRIPTION_ACTIVATED" : "SUBSCRIPTION_DEACTIVATED", "SUCCESS");
        user.changeStatus(enabled, subscriptionActive, clock.instant());
        return details(user);
    }
    private AccountDetails details(UserAccount u) {
        return new AccountDetails(u.id(), u.role().name(), u.brandId(), u.firstName(), u.lastName(), u.isSubscriptionActive(), u.enabled(), u.passwordChangedAt(), u.sessionsRevokedAt());
    }
}
