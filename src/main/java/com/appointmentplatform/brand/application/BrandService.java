package com.appointmentplatform.brand.application;
import com.appointmentplatform.brand.*;
import com.appointmentplatform.brand.domain.Brand;
import com.appointmentplatform.brand.persistence.BrandRepository;
import com.appointmentplatform.shared.ApiException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true)
public class BrandService implements BrandOperations {
    private final BrandRepository repository;
    public BrandService(BrandRepository repository) {
        this.repository = repository;
    }
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID register(BrandRegistration registration) {
        var brand = repository.saveAndFlush(new Brand(UUID.randomUUID(), registration.displayName()));
        return brand.id();
    }
    @PreAuthorize("hasRole('BRAND') and #brandId == authentication.principal.brandId")
    public BrandDetails own(UUID brandId) {
        var brand = repository.findById(brandId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Marka bulunamadı."));
        return new BrandDetails(brand.id(), brand.displayName());
    }
}
