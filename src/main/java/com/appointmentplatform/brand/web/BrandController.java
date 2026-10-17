package com.appointmentplatform.brand.web;
import com.appointmentplatform.brand.application.BrandService;
import com.appointmentplatform.brand.web.dto.BrandResponse;
import com.appointmentplatform.brand.web.dto.CancellationPolicyRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
public class BrandController {
    private final BrandService service;

    public BrandController(BrandService service) {
        this.service = service;
    }

    @GetMapping("/api/panel/brand")
    public BrandResponse own(@AuthenticationPrincipal(expression = "brandId") UUID brandId) {
        return BrandResponse.from(service.own(brandId));
    }

    @PatchMapping("/api/panel/brand/cancellation-policy")
    public BrandResponse updateCancellationPolicy(
            @AuthenticationPrincipal(expression = "brandId") UUID brandId,
            @Valid @RequestBody CancellationPolicyRequest request
    ) {
        return BrandResponse.from(service.updateCancellationPolicy(brandId, request.cancellationNoticeHours()));
    }
}
