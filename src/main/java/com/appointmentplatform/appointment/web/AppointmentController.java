package com.appointmentplatform.appointment.web;

import com.appointmentplatform.appointment.application.ApplicationService;
import com.appointmentplatform.appointment.application.DailyBranchAppointmentsDetails;
import com.appointmentplatform.appointment.web.dto.AppointmentCreationResponse;
import com.appointmentplatform.appointment.web.dto.AvailableAppointmentSlotResponse;
import com.appointmentplatform.appointment.web.dto.AppointmentCreateRequest;
import com.appointmentplatform.appointment.web.dto.DailyBranchAppointmentsResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/panel/appointment")
public class AppointmentController {

    private final ApplicationService applicationService;

    public AppointmentController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }


    @GetMapping("/available-slot/{serviceId}/{employeeId}/{date}")
    public AvailableAppointmentSlotResponse getAvailableStartTimes(
            @AuthenticationPrincipal(expression = "brandId") UUID brandId,
            @PathVariable UUID serviceId,
            @PathVariable UUID employeeId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ){


        return new AvailableAppointmentSlotResponse(applicationService.getAvailableStartTimes(brandId,serviceId,employeeId,date));
    }


    @PostMapping("/create")
    @ResponseStatus(HttpStatus.CREATED)
    public AppointmentCreationResponse createByBrand(
            @AuthenticationPrincipal(expression = "brandId") UUID brandId,
            @RequestBody @Valid AppointmentCreateRequest request
    ){
        return AppointmentCreationResponse.from(applicationService.createByBrand(brandId, request));
    }


    @PostMapping("/approve-appointment/{appointmentId}")
    public void approveAppointment(
            @AuthenticationPrincipal(expression = "brandId") UUID brandId,
            @PathVariable("appointmentId") UUID appointmentId
    ){
        applicationService.approveAppointment(brandId,appointmentId);
    }

    @PostMapping("/reject-appointment/{appointmentId}")
    public void rejectAppointment(
            @AuthenticationPrincipal(expression = "brandId") UUID brandId,
            @PathVariable("appointmentId") UUID appointmentId
    ){
        applicationService.rejectAppointment(brandId,appointmentId);
    }

    @PostMapping("/cancel-appointment/{appointmentId}")
    public void cancelAppointment(
            @AuthenticationPrincipal(expression = "brandId") UUID brandId,
            @PathVariable UUID appointmentId
    ) {
        applicationService.cancelAppointment(brandId, appointmentId);
    }

    @GetMapping("/{branchId}/{date}")
    public DailyBranchAppointmentsResponse dailyAppointments(
            @AuthenticationPrincipal(expression = "brandId") UUID brandId,
            @PathVariable UUID branchId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date

    ){
        return DailyBranchAppointmentsResponse.from(applicationService.showDailyAppointments(brandId,branchId,date));
    }
}
