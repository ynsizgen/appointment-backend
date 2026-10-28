package com.appointmentplatform.brand.domain;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;
@Entity
@Table(name = "brands", schema = "appointment")
public class Brand {
    @Id
    private UUID id;
    @Column(nullable = false, length = 200)
    private String displayName;
    @Column(nullable = false, unique = true, updatable = false, length = 120)
    private String slug;
    @CreationTimestamp(source = org.hibernate.annotations.SourceType.DB)
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private int cancellationNoticeHours;

    public void changeCancellationNoticeHours(int hours) {
        if (hours < 0 || hours > 168) {
            throw new IllegalArgumentException("İptal süresi 0 ile 168 saat arasında olmalıdır.");
        }
        this.cancellationNoticeHours = hours;
    }

    protected Brand() {

    }
    public Brand(UUID id, String displayName) {
        this.id = id;
        this.displayName = displayName;
        this.slug = BrandSlug.generate(displayName, id);
    }
    public UUID id() {
        return id;
    }
    public String slug() {
        return slug;
    }
    public String displayName() {
        return displayName;
    }
    public Instant createdAt() {
        return createdAt;
    }
    public int cancellationNoticeHours() {
        return cancellationNoticeHours;
    }
}
