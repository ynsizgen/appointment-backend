package com.appointmentplatform.branch;

import java.util.UUID;

public record PublicBranchDetails(UUID id, String name, String address, String googleMapsUrl) {}
