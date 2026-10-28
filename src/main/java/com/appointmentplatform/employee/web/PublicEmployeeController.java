package com.appointmentplatform.employee.web;

import com.appointmentplatform.employee.application.EmployeeService;
import com.appointmentplatform.employee.web.dto.EmployeeResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/public/employee")
public class PublicEmployeeController {

    private final EmployeeService employeeService;

    public PublicEmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping("/branches/{branchId}/services/{serviceId}")
    public List<EmployeeResponse> showAvailableEmployees(@PathVariable UUID branchId, @PathVariable UUID serviceId) {

        return employeeService.showAvailableEmployees(branchId, serviceId)
                .stream()
                .map(EmployeeResponse::from)
                .toList();

    }
}
