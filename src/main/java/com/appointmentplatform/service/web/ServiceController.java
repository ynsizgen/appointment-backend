package com.appointmentplatform.service.web;

import com.appointmentplatform.service.application.ServiceService;
import com.appointmentplatform.service.web.dto.ServiceRequest;
import com.appointmentplatform.service.web.dto.ServiceResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
public class ServiceController {

    private final ServiceService serviceService;

    public ServiceController(ServiceService serviceService) {
        this.serviceService = serviceService;
    }

    @PostMapping("/api/panel/addService")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void addService(@AuthenticationPrincipal(expression = "brandId") UUID brandId, @Valid @RequestBody ServiceRequest serviceRequest){
        serviceService.addService(brandId,serviceRequest);
    }

    @GetMapping("/api/panel/services")
    public List<ServiceResponse> getService(@AuthenticationPrincipal(expression = "brandId") UUID brandId){
        return serviceService.ownServices(brandId).
                stream().
                map(ServiceResponse::from).
                toList();
    }

}
