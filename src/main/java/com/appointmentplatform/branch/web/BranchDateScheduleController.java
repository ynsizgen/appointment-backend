package com.appointmentplatform.branch.web;
import com.appointmentplatform.branch.application.BranchDateScheduleService;
import com.appointmentplatform.branch.web.dto.*;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.UUID;
@RestController
@RequestMapping("/api/panel/branches/{branchId}/schedule/{date}")
public class BranchDateScheduleController {

    private final BranchDateScheduleService service;

    public BranchDateScheduleController(BranchDateScheduleService service) {
        this.service = service;
    }

    @GetMapping
    public BranchDailyScheduleResponse daily(@AuthenticationPrincipal(expression = "brandId") UUID brandId,
            @PathVariable("branchId") UUID targetBranchId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return BranchDailyScheduleResponse.from(service.daily(brandId, targetBranchId, date));
    }
    @PutMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void replace(@AuthenticationPrincipal(expression = "brandId") UUID brandId,
            @PathVariable("branchId") UUID targetBranchId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody BranchDateScheduleRequest request) {
        service.replace(brandId, targetBranchId, date, request);
    }
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@AuthenticationPrincipal(expression = "brandId") UUID brandId,
            @PathVariable("branchId") UUID targetBranchId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        service.reset(brandId, targetBranchId, date);
    }
}
