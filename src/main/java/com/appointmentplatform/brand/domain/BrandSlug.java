package com.appointmentplatform.brand.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

final class BrandSlug {
    private BrandSlug() {}

    static String generate(String displayName, UUID id) {
        String prefix = Normalizer.normalize(displayName, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replace('ı', 'i')
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (prefix.isEmpty()) {
            prefix = "brand";
        }
        prefix = prefix.substring(0, Math.min(prefix.length(), 80)).replaceAll("-$", "");
        return prefix + "-" + id.toString().replace("-", "");
    }
}
