package com.appointmentplatform.employee.web;
import com.appointmentplatform.employee.application.EmployeeScheduleService;
import com.appointmentplatform.employee.web.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/panel/employee/{employeeId}")
public class EmployeeScheduleController {

    private final EmployeeScheduleService service;

    public EmployeeScheduleController(EmployeeScheduleService service) {
        this.service = service;
    }
    @GetMapping("/working-hours")
    public EmployeeWeeklyScheduleResponse weekly(@AuthenticationPrincipal(expression = "brandId") UUID brandId,
            @PathVariable UUID employeeId) {
        return EmployeeWeeklyScheduleResponse.from(service.weekly(brandId, employeeId));
    }
    @PutMapping("/working-hours")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void replaceWeekly(@AuthenticationPrincipal(expression = "brandId") UUID brandId,
            @PathVariable UUID employeeId, @Valid @RequestBody EmployeeWeeklyScheduleRequest request) {
        service.replaceWeekly(brandId, employeeId, request);
    }
    @DeleteMapping("/working-hours")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetWeekly(@AuthenticationPrincipal(expression = "brandId") UUID brandId, @PathVariable UUID employeeId) {
        service.resetWeekly(brandId, employeeId);
    }
    @GetMapping("/schedule/{date}")
    public EmployeeDailyScheduleResponse daily(
            @AuthenticationPrincipal(expression = "brandId") UUID brandId,
            @PathVariable UUID employeeId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return EmployeeDailyScheduleResponse.from(service.daily(brandId, employeeId, date));
    }
    @PutMapping("/schedule/{date}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void replaceDate(@AuthenticationPrincipal(expression = "brandId") UUID brandId,
            @PathVariable UUID employeeId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @Valid @RequestBody EmployeeDateScheduleRequest request) {
        service.replaceDate(brandId, employeeId, date, request);
    }
    @DeleteMapping("/schedule/{date}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetDate(@AuthenticationPrincipal(expression = "brandId") UUID brandId,
            @PathVariable UUID employeeId, @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        service.resetDate(brandId, employeeId, date);
    }
}
