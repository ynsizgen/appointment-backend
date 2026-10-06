package com.appointmentplatform.branch.application;
import java.util.UUID;

public record BranchDetails(UUID id, UUID brandId, String name, String address, String googleMapsUrl, boolean acceptingAppointments) {}
