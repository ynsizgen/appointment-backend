package com.appointmentplatform.service.persistence;

import com.appointmentplatform.service.domain.Service;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ServicesRepository extends JpaRepository<Service, UUID> {
    List<Service> findByBrandId(UUID brandId);
}
