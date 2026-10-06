package com.appointmentplatform.identity.web;
import com.appointmentplatform.identity.application.UserAdministrationService;
import com.appointmentplatform.identity.web.dto.*;
import jakarta.validation.Valid;
import java.util.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/admin/users")
public class UserAdministrationController {
    private final UserAdministrationService service;
    public UserAdministrationController(UserAdministrationService service) { this.service = service; }
    @GetMapping
    public List<AccountResponse> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(page, size).stream().map(AccountResponse::from).toList();
    }
    @PatchMapping("/{id}/status")
    public AccountResponse updateStatus(@AuthenticationPrincipal(expression = "id") UUID actorId,
            @PathVariable UUID id, @Valid @RequestBody UserStatusRequest input) {
        return AccountResponse.from(service.updateStatus(actorId, id, input.enabled(), input.subscriptionActive()));
    }
}
