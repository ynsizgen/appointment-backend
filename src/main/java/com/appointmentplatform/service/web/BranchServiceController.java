package com.appointmentplatform.service.web;

import com.appointmentplatform.service.application.ServiceService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/panel/branches/{branchId}/services/{serviceId}")
public class BranchServiceController {

    private final ServiceService serviceService;

    public BranchServiceController(ServiceService serviceService) {
        this.serviceService = serviceService;
    }

    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void assignToBranch(
            @AuthenticationPrincipal(expression = "brandId") UUID brandId,
            @PathVariable UUID branchId,
            @PathVariable UUID serviceId
    ) {
        serviceService.assignToBranch(brandId, branchId, serviceId);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unassignFromBranch(
            @AuthenticationPrincipal(expression = "brandId") UUID brandId,
            @PathVariable UUID branchId,
            @PathVariable UUID serviceId
    ) {
        serviceService.unassignFromBranch(brandId, branchId, serviceId);
    }
}
