package com.appointmentplatform.employee.web;

import com.appointmentplatform.employee.application.EmployeeService;
import com.appointmentplatform.employee.application.EmployeeServiceAssignmentDetails;
import com.appointmentplatform.employee.domain.Employee;
import com.appointmentplatform.employee.web.dto.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/panel/employee")
public class EmployeeController {
    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }
    @GetMapping("/{employeeId}")
    public EmployeeResponse getEmployeeById(
            @AuthenticationPrincipal(expression = "brandId") UUID brandId,
            @PathVariable UUID employeeId) {
        return EmployeeResponse.from(employeeService.getEmployeeById(brandId,employeeId));
    }

    @GetMapping("/{branchId}/showEmployees")
    public List<EmployeeResponse> getEmployeeByBranchId(
            @AuthenticationPrincipal(expression = "brandId") UUID brandId,
            @PathVariable UUID branchId) {
        return employeeService.getEmployeeByBranchId(branchId,brandId).
                stream().
                map(EmployeeResponse::from).
                toList();
    }

    @PostMapping("/{branchId}/employees")
    @ResponseStatus(HttpStatus.CREATED)
    public EmployeeResponse addEmployee(
            @AuthenticationPrincipal(expression = "brandId") UUID brandId,
            @PathVariable UUID branchId,
            @Valid @RequestBody EmployeeRequest request) {

        return EmployeeResponse.from(
                employeeService.addEmployee(brandId, branchId, request)
        );
    }
    @PostMapping("/assign")
    public EmployeeServiceAssignmentResponse assignServiceToEmployee(
            @AuthenticationPrincipal(expression = "brandId") UUID brandId,
            @Valid @RequestBody EmployeeServiceAssignmentRequest request
            ) {
        return EmployeeServiceAssignmentResponse.from(employeeService.assignServiceToEmployee(brandId, request));
    }

    @DeleteMapping("/{employeeId}/services/{serviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unassignServiceFromEmployee(
            @AuthenticationPrincipal(expression = "brandId") UUID brandId,
            @PathVariable UUID employeeId,
            @PathVariable UUID serviceId) {

        employeeService.unassignServiceFromEmployee(brandId, employeeId, serviceId);
    }

    @GetMapping("/{employeeId}/services")
    public List<AssignedServiceResponse> getEmployeeServices(
            @AuthenticationPrincipal(expression = "brandId") UUID brandId,
            @PathVariable UUID employeeId) {
        return employeeService.getEmployeeServices(brandId,employeeId)
                .stream()
                .map(AssignedServiceResponse::from)
                .toList();
    }

    @PatchMapping("/{employeeId}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changeEmployeeStatus(
            @AuthenticationPrincipal(expression = "brandId") UUID brandId,
            @PathVariable UUID employeeId,
            @Valid @RequestBody EmployeeStatusRequest  request
            ){
        employeeService.changeEmployeeStatus(brandId,employeeId,request);
    }

}
