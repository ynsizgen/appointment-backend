package com.appointmentplatform.service.application;


import com.appointmentplatform.service.web.dto.ServiceRequest;
import com.appointmentplatform.service.persistence.ServicesRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ServiceService {

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
}
