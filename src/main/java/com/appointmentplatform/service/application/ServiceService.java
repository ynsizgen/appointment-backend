package com.appointmentplatform.service.application;


import com.appointmentplatform.branch.BranchOperations;
import com.appointmentplatform.service.ServiceDetails;
import com.appointmentplatform.service.ServiceOperations;
import com.appointmentplatform.service.domain.Service;
import com.appointmentplatform.service.domain.BranchServiceAssignment;
import com.appointmentplatform.service.domain.BranchServiceAssignmentId;
import com.appointmentplatform.service.persistence.BranchServiceAssignmentRepository;
import com.appointmentplatform.service.web.dto.ServiceRequest;
import com.appointmentplatform.service.persistence.ServicesRepository;
import com.appointmentplatform.shared.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@org.springframework.stereotype.Service
@Transactional(readOnly = true)
public class ServiceService implements ServiceOperations {

    private final ServicesRepository servicesRepository;

    private final BranchOperations branchOperations;

    private final BranchServiceAssignmentRepository assignments;

    public ServiceService(ServicesRepository servicesRepository, BranchOperations branchOperations,
                          BranchServiceAssignmentRepository assignments) {
        this.servicesRepository = servicesRepository;
        this.branchOperations = branchOperations;
        this.assignments = assignments;
    }

    @PreAuthorize("hasRole('BRAND') and #brandId == authentication.principal.brandId")
    @Transactional
    public void addService(UUID brandId, ServiceRequest serviceRequest ) {
        servicesRepository.saveAndFlush(new com.appointmentplatform.service.domain.Service(UUID.randomUUID(),brandId,serviceRequest.displayName().strip(),serviceRequest.durationMinutes()));
    }

    @PreAuthorize("hasRole('BRAND') and #brandId == authentication.principal.brandId")
    public List<ServiceDetails> ownServices(UUID brandId) {
        return servicesRepository.findByBrandId(brandId).
                stream().
                map(this::details).
                toList();
    }

    @PreAuthorize("hasRole('BRAND') and #brandId == authentication.principal.brandId")
    @Transactional
    public void assignToBranch(UUID brandId, UUID branchId, UUID serviceId) {
        branchOperations.requireOwnedBy(branchId, brandId);
        lockOwnedService(serviceId, brandId);
        var id = new BranchServiceAssignmentId(branchId, serviceId);
        if (!assignments.existsById(id)) {
            assignments.save(new BranchServiceAssignment(branchId, serviceId, brandId));
        }
    }

    @PreAuthorize("hasRole('BRAND') and #brandId == authentication.principal.brandId")
    @Transactional
    public void unassignFromBranch(UUID brandId, UUID branchId, UUID serviceId) {
        branchOperations.requireOwnedBy(branchId, brandId);
        lockOwnedService(serviceId, brandId);
        var id = new BranchServiceAssignmentId(branchId, serviceId);
        assignments.findById(id).ifPresent(assignments::delete);
    }

    private void lockOwnedService(UUID serviceId, UUID brandId) {
        // Lock the existing service row: the assignment may not exist yet.
        servicesRepository.findOwnedByForUpdate(serviceId, brandId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Service bulunamadı."));
    }

    public List<ServiceDetails> getBranchServices(UUID branchId) {
        branchOperations.brandId(branchId);
        return servicesRepository.findAssignedToBranch(branchId).stream()
                .map(this::details)
                .toList();
    }

    ServiceDetails details(com.appointmentplatform.service.domain.Service service) {
        return new ServiceDetails(service.getId(),service.getBrandId(),service.getDisplayName(), service.getDurationMinutes());
    }

    @Override
    public void requireOwnedBy(UUID serviceId, UUID brandId) {
        if (!servicesRepository.existsByIdAndBrandId(serviceId, brandId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Service bulunamadı.");
        }
    }

    @Override
    public ServiceDetails findById(UUID id) {
        Service service = servicesRepository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,"Service bulunamadı."));
        return new ServiceDetails(service.getId(),service.getBrandId(),service.getDisplayName(),service.getDurationMinutes());
    }

    @Override
    public void requireAssignedToBranch(UUID serviceId, UUID branchId) {
        var id = new BranchServiceAssignmentId(branchId, serviceId);

        if (!assignments.existsById(id)) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "Bu hizmet seçilen şubede sunulmuyor."
            );
        }
    }

}
