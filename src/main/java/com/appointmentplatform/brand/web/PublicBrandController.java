package com.appointmentplatform.brand.web;

import com.appointmentplatform.brand.application.BrandService;
import com.appointmentplatform.brand.web.dto.PublicBrandResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;

@RestController
@RequestMapping("/api/public")
public class PublicBrandController {
    private final BrandService service;

    public PublicBrandController(BrandService service) {
        this.service = service;
    }

    @GetMapping("/brands/{slug}")
    public PublicBrandResponse details(@PathVariable String slug) {
        return PublicBrandResponse.from(service.publicDetails(slug));
    }
}
