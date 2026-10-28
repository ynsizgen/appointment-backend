package com.appointmentplatform.service.web;

import com.appointmentplatform.service.application.ServiceService;
import com.appointmentplatform.service.web.dto.PublicServiceResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/public/branches/{branchId}/services")
public class PublicServiceController {

    private final ServiceService serviceService;

    public PublicServiceController(ServiceService serviceService) {
        this.serviceService = serviceService;
    }

    @GetMapping
    public List<PublicServiceResponse> getBranchServices(@PathVariable UUID branchId) {
        return serviceService.getBranchServices(branchId).stream()
                .map(PublicServiceResponse::from)
                .toList();
    }
}
