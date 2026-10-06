package com.appointmentplatform.brand.web;
import com.appointmentplatform.brand.application.BrandService;
import com.appointmentplatform.brand.web.dto.BrandResponse;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
public class BrandController {
    private final BrandService service;
    public BrandController(BrandService service) { this.service = service; }
    @GetMapping("/api/panel/brand")
    public BrandResponse own(@AuthenticationPrincipal(expression = "brandId") UUID brandId) {
        return BrandResponse.from(service.own(brandId));
    }
}
