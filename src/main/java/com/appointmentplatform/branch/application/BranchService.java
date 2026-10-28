package com.appointmentplatform.branch.application;
import com.appointmentplatform.branch.BranchDailySchedule;
import com.appointmentplatform.branch.PublicBranchDetails;
import com.appointmentplatform.branch.BranchOperations;
import com.appointmentplatform.branch.BranchWorkingInterval;
import com.appointmentplatform.branch.domain.Branch;
import com.appointmentplatform.branch.domain.BranchWorkingHours;
import com.appointmentplatform.branch.persistence.BranchWorkingHoursRepository;
import com.appointmentplatform.branch.web.dto.*;
import java.net.URI;
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
    private final BranchWorkingHoursRepository hours;
    private final BranchDateScheduleService dates;
    public BranchService(BranchRepository repository, Audit audit, BranchWorkingHoursRepository hours, BranchDateScheduleService dates) {
        this.dates = dates;
        this.repository = repository; this.audit = audit;
        this.hours = hours;
    }

    @Override
    public List<PublicBranchDetails> acceptingBranches(UUID brandId) {
        return repository.findByBrandIdAndAcceptingAppointmentsTrueOrderByCreatedAtAscIdAsc(brandId)
                .stream()
                .map(branch -> new PublicBranchDetails(branch.id(), branch.name(), branch.address(), branch.googleMapsUrl()))
                .toList();
    }

    @Transactional
    @PreAuthorize("hasRole('BRAND') and #brandId == authentication.principal.brandId and #actorId == authentication.principal.id")
    public BranchDetails create(UUID actorId, UUID brandId, BranchCreateRequest request) {
        validateMapsUrl(request.googleMapsUrl());
        validateHours(request.workingHours());
        var branch = repository.saveAndFlush(new Branch(UUID.randomUUID(), brandId,
                request.name().strip(), request.address().strip(), request.googleMapsUrl()));
        saveHours(branch.id(), request.workingHours());
        audit.record(actorId, brandId, "BRANCH_CREATED", "SUCCESS");
        return details(branch);
    }

    @PreAuthorize("hasRole('BRAND') and #brandId == authentication.principal.brandId")
    public List<WorkingHoursDetails> workingHours(UUID brandId, UUID branchId) {
        requireOwnedBy(branchId, brandId);
        return hours.findAllByBranchId(branchId).stream()
                .sorted(Comparator.comparing(BranchWorkingHours::dayOfWeek).thenComparing(BranchWorkingHours::startTime))
                .map(h -> new WorkingHoursDetails(h.dayOfWeek(), h.startTime(), h.endTime())).toList();
    }

    @Transactional
    @PreAuthorize("hasRole('BRAND') and #brandId == authentication.principal.brandId and #actorId == authentication.principal.id")
    public void replaceWorkingHours(UUID actorId, UUID brandId, UUID branchId, WeeklyScheduleRequest request) {
        repository.findForUpdate(branchId, brandId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Şube bulunamadı."));
        validateHours(request.workingHours());
        hours.deleteByBranchId(branchId);
        hours.flush();
        saveHours(branchId, request.workingHours());
        audit.record(actorId, brandId, "BRANCH_WORKING_HOURS_CHANGED", "SUCCESS");
    }

    private void saveHours(UUID branchId, List<WorkingHoursRequest> intervals) {
        hours.saveAll(intervals.stream().map(h -> new BranchWorkingHours(UUID.randomUUID(), branchId,
                h.dayOfWeek(), h.startTime(), h.endTime())).toList());
    }

    private void validateHours(List<WorkingHoursRequest> intervals) {
        var sorted = intervals.stream().sorted(Comparator.comparing(WorkingHoursRequest::dayOfWeek)
                .thenComparing(WorkingHoursRequest::startTime)).toList();
        WorkingHoursRequest previous = null;
        for (var current : sorted) {
            if (!current.startTime().isBefore(current.endTime()))
                throw new ApiException(HttpStatus.BAD_REQUEST, "Bitiş saati başlangıç saatinden sonra olmalıdır.");
            if (previous != null && previous.dayOfWeek() == current.dayOfWeek()
                    && current.startTime().isBefore(previous.endTime()))
                throw new ApiException(HttpStatus.BAD_REQUEST, "Aynı günün çalışma aralıkları çakışamaz.");
            previous = current;
        }
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
    public void requireAcceptingAppointments(UUID branchId, UUID brandId) {
        var branch = repository.findById(branchId)
                .filter(value -> value.brandId().equals(brandId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Şube bulunamadı."));

        if (!branch.acceptingAppointments()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Bu şube şu anda yeni randevu kabul etmiyor.");
        }
    }

    @Override
    public UUID brandId(UUID branchId) {
        return repository.findById(branchId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Şube bulunamadı."))
                .brandId();
    }

    @Override
    public void requireOwnedBy(UUID branchId,UUID brandId) {
        if (!repository.existsByIdAndBrandId(branchId,brandId)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Şube bulunamadı.");
        }
    }

    @Override
    @PreAuthorize("hasRole('BRAND') and #brandId == authentication.principal.brandId")
    public List<BranchWorkingInterval> weeklyHours(UUID branchId, UUID brandId) {
        requireOwnedBy(branchId, brandId);
        return hours.findAllByBranchId(branchId).stream()
                .map(h -> new BranchWorkingInterval(h.dayOfWeek(), h.startTime(), h.endTime())).toList();
    }

    @Override
    public BranchDailySchedule dailyHours(UUID branchId, UUID brandId, java.time.LocalDate date) {
        return dates.daily(brandId, branchId, date);
    }

    private void validateMapsUrl(String value) {
        if (value == null || value.isBlank()) return;
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
                throw new IllegalArgumentException();
        } catch (IllegalArgumentException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Konum bağlantısı geçerli bir HTTPS adresi olmalıdır.");
        }
    }
}
