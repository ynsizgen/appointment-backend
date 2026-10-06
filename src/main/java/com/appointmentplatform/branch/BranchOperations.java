package com.appointmentplatform.branch;
import java.util.UUID;
/** Registration contract; participates in the caller's transaction. */
public interface BranchOperations {
    UUID registerInitial(UUID brandId, String address, String googleMapsUrl);
}
