package com.appointmentplatform.appointment.web;

import com.appointmentplatform.appointment.application.ApplicationService;
import com.appointmentplatform.appointment.web.dto.AppointmentCreateRequest;
import com.appointmentplatform.appointment.web.dto.AppointmentCreationResponse;
import com.appointmentplatform.appointment.web.dto.AvailableAppointmentSlotResponse;
import com.appointmentplatform.appointment.web.dto.CustomerAppointmentResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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
            @RequestBody @Valid AppointmentCreateRequest request,
            @RequestHeader(value = "X-Phone-Verification", required = false) String verificationToken) {

        return AppointmentCreationResponse.from(
                applicationService.createByCustomer(branchId, request, verificationToken));
    }

    @GetMapping("/details")
    public CustomerAppointmentResponse appointmentDetails(
            @RequestHeader("X-Appointment-Token") String token
    ) {
        return CustomerAppointmentResponse.from(applicationService.appointmentDetails(token));
    }

    @PostMapping("/cancel-appointment")
    public void cancelAppointmentByCustomer(
            @RequestHeader("X-Appointment-Token") String token
    ) {
        applicationService.cancelAppointmentByCustomer(token);
    }

    @GetMapping("/available-slot/{branchId}/{serviceId}/{employeeId}/{date}")
    public AvailableAppointmentSlotResponse getPublicAvailableStartTimes(
            @PathVariable UUID branchId,
            @PathVariable UUID serviceId,
            @PathVariable UUID employeeId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ){
        return new AvailableAppointmentSlotResponse(applicationService.getPublicAvailableStartTimes(branchId,serviceId,employeeId,date));
    }

}