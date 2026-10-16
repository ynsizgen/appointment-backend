package com.appointmentplatform.branch.web;
import com.appointmentplatform.branch.application.BranchService;
import com.appointmentplatform.branch.web.dto.*;
import jakarta.validation.Valid;
import java.util.*;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/panel/branches")
public class BranchController {
    private final BranchService service;

    public BranchController(BranchService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public BranchResponse create(
            @AuthenticationPrincipal(expression = "id") UUID actorId,
            @AuthenticationPrincipal(expression = "brandId") UUID brandId,
            @Valid @RequestBody BranchCreateRequest request) {
        return BranchResponse.from(service.create(actorId, brandId, request));
    }

    @GetMapping("/{id}/working-hours")
    public List<WorkingHoursResponse> workingHours(@AuthenticationPrincipal(expression = "brandId") UUID brandId,
            @PathVariable UUID id) {
        return service.workingHours(brandId, id).stream().map(WorkingHoursResponse::from).toList();
    }

    @PutMapping("/{id}/working-hours")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void replaceWorkingHours(@AuthenticationPrincipal(expression = "id") UUID actorId,
            @AuthenticationPrincipal(expression = "brandId") UUID brandId,
            @PathVariable UUID id,
            @Valid @RequestBody WeeklyScheduleRequest request) {
        service.replaceWorkingHours(actorId, brandId, id, request);
    }

    @GetMapping
    public List<BranchResponse> own(@AuthenticationPrincipal(expression = "brandId") UUID brandId) {
        return service.own(brandId).stream().map(BranchResponse::from).toList();
    }
    @PatchMapping("/{id}/appointment-acceptance")
    public BranchResponse changeAcceptance(@AuthenticationPrincipal(expression = "id") UUID actorId,
            @AuthenticationPrincipal(expression = "brandId") UUID brandId,
            @PathVariable UUID id, @Valid @RequestBody AppointmentAcceptanceRequest input) {
        return BranchResponse.from(service.changeAcceptance(actorId, brandId, id, input.acceptingAppointments()));
    }
}
