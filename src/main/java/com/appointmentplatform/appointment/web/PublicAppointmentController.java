package com.appointmentplatform.appointment.web;

import com.appointmentplatform.appointment.application.ApplicationService;
import com.appointmentplatform.appointment.web.dto.AppointmentCreateRequest;
import com.appointmentplatform.appointment.web.dto.AppointmentCreationResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/public/appointment")
public class PublicAppointmentController {

    private final ApplicationService applicationService;

    public PublicAppointmentController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/create/{branchId}")
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentCreationResponse createByCustomer(
            @PathVariable UUID branchId,
            @RequestBody @Valid AppointmentCreateRequest request) {

        return AppointmentCreationResponse.from(
                applicationService.createByCustomer(branchId, request));
    }
}