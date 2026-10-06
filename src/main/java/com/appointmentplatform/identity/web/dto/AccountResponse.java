package com.appointmentplatform.identity.web.dto;
import com.appointmentplatform.identity.application.AccountDetails;
import java.util.UUID;
public record AccountResponse(UUID id, String role, UUID brandId, String firstName, String lastName, boolean enabled, boolean subscriptionActive) {
    public static AccountResponse from(AccountDetails account) {
        return new AccountResponse(account.id(), account.role(), account.brandId(), account.firstName(), account.lastName(), account.enabled(), account.subscriptionActive());
    }
}
