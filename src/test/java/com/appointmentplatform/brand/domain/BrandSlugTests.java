package com.appointmentplatform.brand.domain;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BrandSlugTests {
    @Test
    void turkishNameProducesUrlSafeStableSlug() {
        UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");
        Brand brand = new Brand(id, "  İĞDE Şık Çözüm & Kuaför  ");
        assertThat(brand.slug()).isEqualTo("igde-sik-cozum-kuafor-00000000000000000000000000000001");
        brand.changeCancellationNoticeHours(24);
        assertThat(brand.slug()).isEqualTo(BrandSlug.generate("  İĞDE Şık Çözüm & Kuaför  ", id));
    }

    @Test
    void identicalNamesHaveDistinctSlugs() {
        assertThat(new Brand(UUID.randomUUID(), "Ada Kuaför").slug())
                .isNotEqualTo(new Brand(UUID.randomUUID(), "Ada Kuaför").slug());
    }

    @Test
    void unsupportedNamesAndLongNamesRemainValid() {
        assertThat(new Brand(UUID.randomUUID(), "💇 中文").slug()).startsWith("brand-");
        String slug = new Brand(UUID.randomUUID(), "a".repeat(200)).slug();
        assertThat(slug.length()).isLessThanOrEqualTo(120);
        assertThat(slug).matches("[a-z0-9]+(-[a-z0-9]+)*");
    }
}
