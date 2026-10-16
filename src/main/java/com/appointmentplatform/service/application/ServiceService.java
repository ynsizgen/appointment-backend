package com.appointmentplatform.service.application;


import com.appointmentplatform.service.ServiceDetails;
import com.appointmentplatform.service.ServiceOperations;
import com.appointmentplatform.service.web.dto.ServiceRequest;
import com.appointmentplatform.service.persistence.ServicesRepository;
import com.appointmentplatform.shared.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ServiceService implements ServiceOperations {

    private final ServicesRepository servicesRepository;

    public ServiceService(ServicesRepository servicesRepository) {
        this.servicesRepository = servicesRepository;
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
        com.appointmentplatform.service.domain.Service service = servicesRepository.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,"Service bulunamadı."));
        return new ServiceDetails(service.getId(),service.getBrandId(),service.getDisplayName(),service.getDurationMinutes());
    }
}
