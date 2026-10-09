package com.appointmentplatform.branch.application;
import com.appointmentplatform.branch.BranchOperations;
import com.appointmentplatform.branch.domain.Branch;
import com.appointmentplatform.branch.persistence.BranchRepository;
import com.appointmentplatform.audit.Audit;
import com.appointmentplatform.shared.ApiException;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(readOnly = true)
public class BranchService implements BranchOperations {
    private final BranchRepository repository;
    private final Audit audit;
    public BranchService(BranchRepository repository, Audit audit) { this.repository = repository; this.audit = audit; }
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID registerInitial(UUID brandId, String address, String googleMapsUrl) {
        return repository.saveAndFlush(new Branch(UUID.randomUUID(), brandId, "Merkez", address, googleMapsUrl)).id();
    }
    @PreAuthorize("hasRole('BRAND') and #brandId == authentication.principal.brandId")
    public List<BranchDetails> own(UUID brandId) {
        return repository.findByBrandIdOrderByCreatedAtAscIdAsc(brandId).stream().map(this::details).toList();
    }
    @Transactional
    @PreAuthorize("hasRole('BRAND') and #brandId == authentication.principal.brandId and #actorId == authentication.principal.id")
    public BranchDetails changeAcceptance(UUID actorId, UUID brandId, UUID branchId, boolean accepting) {
        var branch = repository.findForUpdate(branchId, brandId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Şube bulunamadı."));
        branch.changeAppointmentAcceptance(accepting);
        audit.record(actorId, brandId, accepting ? "BRANCH_ACCEPTANCE_OPENED" : "BRANCH_ACCEPTANCE_CLOSED", "SUCCESS");
        return details(branch);
    }
    private BranchDetails details(Branch b) {
        return new BranchDetails(b.id(), b.brandId(), b.name(), b.address(), b.googleMapsUrl(), b.acceptingAppointments());
    }

    @Override
    @PreAuthorize("hasRole('BRAND') and #brandId == authentication.principal.brandId")
    public void requireOwnedBy(UUID branchId,UUID brandId) {
        if (!repository.existsByIdAndBrandId(branchId,brandId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Şube bulunamadı.");
        }
    }
}
