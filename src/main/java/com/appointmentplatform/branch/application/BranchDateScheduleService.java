package com.appointmentplatform.branch.application;
import com.appointmentplatform.branch.*;
import com.appointmentplatform.branch.domain.*;
import com.appointmentplatform.branch.persistence.*;
import com.appointmentplatform.branch.web.dto.BranchDateScheduleRequest;
import com.appointmentplatform.shared.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
@Transactional(readOnly = true)
@PreAuthorize("hasRole('BRAND') and #brandId == authentication.principal.brandId")
public class BranchDateScheduleService {
    private final BranchRepository branches;
    private final BranchWorkingHoursRepository hours;
    private final BranchScheduleOverrideRepository overrides;
    private final Clock clock;
    public BranchDateScheduleService(BranchRepository branches, BranchWorkingHoursRepository hours,
            BranchScheduleOverrideRepository overrides, Clock clock) {
        this.branches = branches; this.hours = hours; this.overrides = overrides; this.clock = clock;
    }
    public BranchDailySchedule daily(UUID brandId, UUID branchId, LocalDate date) {
        if (!branches.existsByIdAndBrandId(branchId, brandId))
            throw missing();
        var override = overrides.findByBranchIdAndDate(branchId, date);
        var intervals = override.map(branchScheduleOverride -> branchScheduleOverride.intervals().stream().map(h -> new BranchTimeInterval(h.startTime(), h.endTime())).toList()).orElseGet(() -> hours.findAllByBranchId(branchId).stream().filter(h -> h.dayOfWeek() == date.getDayOfWeek())
                .map(h -> new BranchTimeInterval(h.startTime(), h.endTime())).toList());
        return new BranchDailySchedule(date, override.isPresent(), intervals.stream()
                .sorted(Comparator.comparing(BranchTimeInterval::startTime)).toList());
    }
    @Transactional
    public void replace(UUID brandId, UUID branchId, LocalDate date, BranchDateScheduleRequest request) {
        branches.findForUpdate(branchId, brandId).orElseThrow(this::missing);
        requireEditable(date);
        var intervals = request.workingHours().stream()
                .sorted(Comparator.comparing(com.appointmentplatform.branch.web.dto.BranchDateHoursRequest::startTime)).toList();
        LocalTime previousEnd = null;
        for (var h : intervals) {
            if (!h.startTime().isBefore(h.endTime()))
                throw new ApiException(HttpStatus.BAD_REQUEST, "Bitiş saati başlangıç saatinden sonra olmalıdır.");
            if (previousEnd != null && h.startTime().isBefore(previousEnd))
                throw new ApiException(HttpStatus.BAD_REQUEST, "Çalışma aralıkları çakışamaz.");
            previousEnd = h.endTime();
        }
        var override = overrides.findByBranchIdAndDate(branchId, date)
                .orElseGet(() -> new BranchScheduleOverride(branchId, date));
        override.replace(intervals.stream().map(h -> new BranchTimeRange(h.startTime(), h.endTime())).toList());
        overrides.save(override);
    }
    @Transactional
    public void reset(UUID brandId, UUID branchId, LocalDate date) {
        branches.findForUpdate(branchId, brandId).orElseThrow(this::missing);
        requireEditable(date);
        overrides.findByBranchIdAndDate(branchId, date).ifPresent(overrides::delete);
    }
    private void requireEditable(LocalDate date) {
        if (date.isBefore(LocalDate.now(clock.withZone(ZoneId.of("Europe/Istanbul")))))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Geçmiş tarihin çalışma saatleri değiştirilemez.");
    }
    private ApiException missing() {
        return new ApiException(HttpStatus.NOT_FOUND, "Şube bulunamadı.");
    }
}
