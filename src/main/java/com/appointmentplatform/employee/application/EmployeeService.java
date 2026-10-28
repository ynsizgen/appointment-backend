package com.appointmentplatform.employee.application;

import com.appointmentplatform.branch.BranchOperations;
import com.appointmentplatform.employee.EmployeeDetails;
import com.appointmentplatform.employee.EmployeeOperations;
import com.appointmentplatform.employee.domain.Employee;
import com.appointmentplatform.employee.domain.EmployeeServiceAssignment;
import com.appointmentplatform.employee.persistence.EmployeeRepository;
import com.appointmentplatform.employee.persistence.EmployeeServiceAssignmentRepository;
import com.appointmentplatform.employee.web.dto.EmployeeRequest;
import com.appointmentplatform.employee.web.dto.EmployeeServiceAssignmentRequest;
import com.appointmentplatform.employee.web.dto.EmployeeStatusRequest;
import com.appointmentplatform.service.ServiceOperations;
import com.appointmentplatform.shared.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class EmployeeService implements EmployeeOperations {

    private final EmployeeRepository employeeRepository;
    private final BranchOperations branchOperations;
    private final EmployeeServiceAssignmentRepository employeeServiceAssignmentRepository;
    private final ServiceOperations serviceOperations;

    public EmployeeService(EmployeeRepository employeeRepository, BranchOperations branchOperations, EmployeeServiceAssignmentRepository employeeServiceAssignmentRepository, ServiceOperations serviceOperations) {
        this.employeeRepository = employeeRepository;
        this.branchOperations = branchOperations;
        this.employeeServiceAssignmentRepository = employeeServiceAssignmentRepository;
        this.serviceOperations = serviceOperations;
    }

    @PreAuthorize("hasRole('BRAND') and #brandId == authentication.principal.brandId")
    public EmployeeDetails getEmployeeById(UUID brandId, UUID employeeId) {
        Employee employee = employeeRepository.findById(employeeId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, " Employee bulunamadı."));

        branchOperations.requireOwnedBy(employee.getBranchId(), brandId);
        return new EmployeeDetails(employee.getId(), employee.getBranchId(), employee.getFirstName(), employee.getLastName(), employee.isActive());
    }

    @PreAuthorize("hasRole('BRAND') and #brandId == authentication.principal.brandId")
    public List<EmployeeDetails> getEmployeeByBranchId(UUID branchId, UUID brandId) {
        branchOperations.requireOwnedBy(branchId, brandId);

        return employeeRepository.findAllByBranchId(branchId).
                stream().
                map(this::details).
                toList();
    }

    @Transactional
    @PreAuthorize("hasRole('BRAND') and #brandId == authentication.principal.brandId")
    public EmployeeDetails addEmployee(UUID brandId, UUID branchId, EmployeeRequest request) {
        branchOperations.requireOwnedBy(branchId,brandId);

        var employee = employeeRepository.saveAndFlush(new Employee(UUID.randomUUID(), branchId, request.firstName().strip(), request.lastName().strip(), request.active()));

        return details(employee);
    }

    private EmployeeDetails details(Employee employee) {
        return new EmployeeDetails(
                employee.getId(),
                employee.getBranchId(),
                employee.getFirstName(),
                employee.getLastName(),
                employee.isActive()
        );
    }


    @Transactional
    @PreAuthorize("hasRole('BRAND') and #brandId == authentication.principal.brandId")
    public EmployeeServiceAssignmentDetails assignServiceToEmployee(UUID brandId, EmployeeServiceAssignmentRequest request) {
        Employee employee = employeeRepository.findById(request.employeeId()).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, " Employee bulunamadı."));


        branchOperations.requireOwnedBy(employee.getBranchId(), brandId);
        serviceOperations.requireOwnedBy(request.serviceId(), brandId);
        serviceOperations.requireAssignedToBranch(
                request.serviceId(),
                employee.getBranchId()
        );

        if (employeeServiceAssignmentRepository.existsByEmployeeIdAndServiceId(request.employeeId(), request.serviceId())) {
            throw new ApiException(HttpStatus.CONFLICT, "Bu service employee'ye zaten atanmış."
            );
        }

        EmployeeServiceAssignment employeeServiceAssignment = employeeServiceAssignmentRepository.save(new EmployeeServiceAssignment(UUID.randomUUID(),request.employeeId(),request.serviceId()));
        return new EmployeeServiceAssignmentDetails(employeeServiceAssignment.getId(),employeeServiceAssignment.getEmployeeId(),employeeServiceAssignment.getServiceId());
    }

    @Transactional
    @PreAuthorize("hasRole('BRAND') and #brandId == authentication.principal.brandId")
    public void unassignServiceFromEmployee (
            UUID brandId, UUID employeeId, UUID serviceId) {

        var employee = employeeRepository.findById(employeeId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Employee bulunamadı."));

        branchOperations.requireOwnedBy(employee.getBranchId(), brandId);
        serviceOperations.requireOwnedBy(serviceId, brandId);

        employeeServiceAssignmentRepository.deleteByEmployeeIdAndServiceId(employeeId, serviceId);
    }

    @PreAuthorize("hasRole('BRAND') and #brandId == authentication.principal.brandId")
    public List<AssignedServiceDetails> getEmployeeServices(UUID brandId,UUID employeeId) {
        Employee employee = employeeRepository.findById(employeeId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Employee bulunamadı."));
        branchOperations.requireOwnedBy(employee.getBranchId(), brandId);

        return employeeServiceAssignmentRepository.findAllByEmployeeId(employeeId).
                stream().
                map(esa -> new AssignedServiceDetails(employeeId,serviceOperations.findById(esa.getServiceId()))).
                toList();
    }

    @Transactional
    @PreAuthorize("hasRole('BRAND') and #brandId == authentication.principal.brandId")
    public void changeEmployeeStatus(UUID brandId,UUID employeeId,EmployeeStatusRequest request) {

        var employee = employeeRepository.findById(employeeId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,"Employee bulunamadı."));

        branchOperations.requireOwnedBy(employee.getBranchId(), brandId);

        employee.setActive(request.active());

    }

    @Override
    public UUID branchId(UUID employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Employee bulunamadı."))
                .getBranchId();
    }

    @Override
    public void requireServiceAssigned(UUID brandId, UUID employeeId, UUID serviceId) {

        var employee = employeeRepository.findById(employeeId).orElseThrow(() ->
                        new ApiException(HttpStatus.NOT_FOUND, "Employee bulunamadı."));

        branchOperations.requireOwnedBy(employee.getBranchId(), brandId);

        if (!employeeServiceAssignmentRepository.existsByEmployeeIdAndServiceId(employeeId, serviceId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Seçilen hizmet bu çalışana atanmamış."
            );
        }
    }

    @Override
    public List<EmployeeDetails> findAllEmployeeByBranchId(UUID branchId) {
        return employeeRepository.findAllByBranchId(branchId).
                stream().
                map(e-> new EmployeeDetails(
                        e.getId(),
                        e.getBranchId(),
                        e.getFirstName(),
                        e.getLastName(),
                        e.isActive())).toList();
    }

    public List<EmployeeDetails> showAvailableEmployees(UUID branchId, UUID serviceId) {

        serviceOperations.requireAssignedToBranch(serviceId, branchId);

        return employeeRepository.findAvailableEmployees(serviceId, branchId)
                .stream().map(e-> new EmployeeDetails(e.getId(),e.getBranchId(),e.getFirstName(),e.getLastName(),e.isActive()))
                .toList();
    }
}
