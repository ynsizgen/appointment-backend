package com.appointmentplatform.branch.web.dto;
import com.appointmentplatform.branch.application.BranchDetails;
import java.util.UUID;
public record BranchResponse(UUID id, UUID brandId, String name, String address, String googleMapsUrl, boolean acceptingAppointments) {
    public static BranchResponse from(BranchDetails b) {
        return new BranchResponse(b.id(), b.brandId(), b.name(), b.address(), b.googleMapsUrl(), b.acceptingAppointments());
    }
}
