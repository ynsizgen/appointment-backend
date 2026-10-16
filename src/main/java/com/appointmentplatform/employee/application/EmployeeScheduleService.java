package com.appointmentplatform.employee.application;

import com.appointmentplatform.employee.EmployeeDailyScheduleDetails;
import com.appointmentplatform.employee.EmployeeTimeIntervalDetails;

import com.appointmentplatform.branch.BranchOperations;
import com.appointmentplatform.employee.domain.*;
import com.appointmentplatform.employee.persistence.*;
import com.appointmentplatform.employee.EmployeeScheduleOperations;
import com.appointmentplatform.employee.web.dto.*;
import com.appointmentplatform.shared.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class EmployeeScheduleService implements EmployeeScheduleOperations {
    private final EmployeeRepository employees;
    private final EmployeeWeeklyScheduleRepository weekly;
    private final EmployeeScheduleOverrideRepository overrides;
    private final BranchOperations branches;
    private final Clock clock;

    public EmployeeScheduleService(EmployeeRepository employees, EmployeeWeeklyScheduleRepository weekly,
            EmployeeScheduleOverrideRepository overrides, BranchOperations branches, Clock clock) {
        this.employees = employees;
        this.weekly = weekly;
        this.overrides = overrides;
        this.branches = branches;
        this.clock = clock;
    }

    @PreAuthorize("hasRole('BRAND') and #brandId == authentication.principal.brandId")
    public EmployeeWeeklyScheduleDetails weekly(UUID brandId, UUID employeeId) {
        var employee = owned(brandId, employeeId, false);
        return weeklyConfiguration(brandId, employee);
    }

    @Transactional
    @PreAuthorize("hasRole('BRAND') and #brandId == authentication.principal.brandId")
    public void replaceWeekly(UUID brandId, UUID employeeId, EmployeeWeeklyScheduleRequest request) {
        var employee = owned(brandId, employeeId, true);
        var branchHours = branchHours(brandId, employee);
        for (var h : request.workingHours()) {
            if (request.closedDays().contains(h.dayOfWeek()))
                throw new ApiException(HttpStatus.BAD_REQUEST, "Kapalı gün için çalışma saati girilemez.");
        }
        for (var day : DayOfWeek.values()) {
            var intervals = request.workingHours().stream().filter(h -> h.dayOfWeek() == day)
                    .map(h -> new EmployeeTimeIntervalDetails(h.startTime(), h.endTime())).toList();
            validate(intervals, forDay(branchHours, day));
        }
        var schedule = weekly.findById(employeeId).orElseGet(() -> new EmployeeWeeklySchedule(employeeId));
        schedule.replace(request.workingHours().stream()
                .map(h -> new EmployeeWeeklyInterval(h.dayOfWeek(), h.startTime(), h.endTime())).toList());
        schedule.replaceClosedDays(request.closedDays());
        weekly.save(schedule);
    }

    @Transactional
    @PreAuthorize("hasRole('BRAND') and #brandId == authentication.principal.brandId")
    public void resetWeekly(UUID brandId, UUID employeeId) {
        owned(brandId, employeeId, true);
        weekly.findById(employeeId).ifPresent(weekly::delete);
    }

    public EmployeeDailyScheduleDetails daily(UUID brandId, UUID employeeId, LocalDate date) {
        var employee = owned(brandId, employeeId, false);
        var branch = dateBranchHours(brandId, employee, date);
        var override = overrides.findByEmployeeIdAndDate(employeeId, date);
        List<EmployeeTimeIntervalDetails> configured;
        String source;
        if (override.isPresent()) {
            configured = override.get().intervals().stream().map(h -> new EmployeeTimeIntervalDetails(h.startTime(), h.endTime())).toList();
            source = "DATE_OVERRIDE";
        } else {
            var schedule = weekly.findById(employeeId);
            var day = date.getDayOfWeek();
            if (schedule.isPresent() && schedule.get().closedDays().contains(day)) {
                configured = List.of();
                source = "EMPLOYEE_WEEKLY";
            } else {
                var ownHours = schedule.stream().flatMap(s -> s.intervals().stream())
                        .filter(h -> h.dayOfWeek() == day)
                        .map(h -> new EmployeeTimeIntervalDetails(h.startTime(), h.endTime())).toList();
                configured = ownHours.isEmpty() ? branch : ownHours;
                source = ownHours.isEmpty() ? "BRANCH" : "EMPLOYEE_WEEKLY";
            }
        }
        configured = merge(configured);
        var effective = employee.isActive() ? intersect(configured, branch) : List.<EmployeeTimeIntervalDetails>of();
        return new EmployeeDailyScheduleDetails(date, source, employee.isActive(), configured, effective);
    }

    @Transactional
    @PreAuthorize("hasRole('BRAND') and #brandId == authentication.principal.brandId")
    public void replaceDate(UUID brandId, UUID employeeId, LocalDate date, EmployeeDateScheduleRequest request) {
        var employee = owned(brandId, employeeId, true);
        requireEditable(date);
        var intervals = request.workingHours().stream().map(h -> new EmployeeTimeIntervalDetails(h.startTime(), h.endTime())).toList();
        validate(intervals, dateBranchHours(brandId, employee, date));
        var override = overrides.findByEmployeeIdAndDate(employeeId, date)
                .orElseGet(() -> new EmployeeScheduleOverride(employeeId, date));
        override.replace(intervals.stream().map(h -> new EmployeeTimeInterval(h.startTime(), h.endTime())).toList());
        overrides.save(override);
    }

    @Transactional
    @PreAuthorize("hasRole('BRAND') and #brandId == authentication.principal.brandId")
    public void resetDate(UUID brandId, UUID employeeId, LocalDate date) {
        owned(brandId, employeeId, true);
        requireEditable(date);
        overrides.findByEmployeeIdAndDate(employeeId, date).ifPresent(overrides::delete);
    }

    private Employee owned(UUID brandId, UUID employeeId, boolean lock) {
        var employee = (lock ? employees.findForUpdate(employeeId) : employees.findById(employeeId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Employee bulunamadı."));
        branches.requireOwnedBy(employee.getBranchId(), brandId);
        return employee;
    }

    private EmployeeWeeklyScheduleDetails weeklyConfiguration(UUID brandId, Employee employee) {
        var schedule = weekly.findById(employee.getId());
        var branch = branchHours(brandId, employee);
        var configured = schedule.stream().flatMap(s -> s.intervals().stream())
                .map(h -> new EmployeeWeeklyIntervalDetails(h.dayOfWeek(), h.startTime(), h.endTime()))
                .sorted(Comparator.comparing(EmployeeWeeklyIntervalDetails::dayOfWeek)
                        .thenComparing(EmployeeWeeklyIntervalDetails::startTime)).toList();
        Set<DayOfWeek> closed = schedule.map(s -> Set.copyOf(s.closedDays())).orElse(Set.of());
        var inherited = new ArrayList<DayOfWeek>();
        var effective = new ArrayList<EmployeeWeeklyIntervalDetails>();
        for (var day : DayOfWeek.values()) {
            if (closed.contains(day)) continue;
            var own = forDay(configured, day);
            var branchDay = forDay(branch, day);
            if (own.isEmpty()) {
                inherited.add(day);
                own = branchDay;
            }
            for (var interval : intersect(own, branchDay)) {
                effective.add(new EmployeeWeeklyIntervalDetails(day, interval.startTime(), interval.endTime()));
            }
        }
        return new EmployeeWeeklyScheduleDetails(inherited.size() == 7, effective, configured,
                closed.stream().sorted().toList(), inherited);
    }

    private List<EmployeeWeeklyIntervalDetails> branchHours(UUID brandId, Employee employee) {
        return branches.weeklyHours(employee.getBranchId(), brandId).stream()
                .map(h -> new EmployeeWeeklyIntervalDetails(h.dayOfWeek(), h.startTime(), h.endTime()))
                .sorted(Comparator.comparing(EmployeeWeeklyIntervalDetails::dayOfWeek).thenComparing(EmployeeWeeklyIntervalDetails::startTime)).toList();
    }

    private List<EmployeeTimeIntervalDetails> forDay(List<EmployeeWeeklyIntervalDetails> hours, DayOfWeek day) {
        return hours.stream().filter(h -> h.dayOfWeek() == day).map(h -> new EmployeeTimeIntervalDetails(h.startTime(), h.endTime())).toList();
    }

    private List<EmployeeTimeIntervalDetails> dateBranchHours(UUID brandId, Employee employee, LocalDate date) {
        return branches.dailyHours(employee.getBranchId(), brandId, date).workingHours().stream()
                .map(h -> new EmployeeTimeIntervalDetails(h.startTime(), h.endTime())).toList();
    }

    private void requireEditable(LocalDate date) {
        if (date.isBefore(LocalDate.now(clock.withZone(ZoneId.of("Europe/Istanbul")))))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Geçmiş tarihin çalışma saatleri değiştirilemez.");
    }

    private void validate(List<EmployeeTimeIntervalDetails> intervals, List<EmployeeTimeIntervalDetails> branchHours) {
        var sorted = intervals.stream().sorted(Comparator.comparing(EmployeeTimeIntervalDetails::startTime)).toList();
        EmployeeTimeIntervalDetails previous = null;
        for (var current : sorted) {
            if (!current.startTime().isBefore(current.endTime()))
                throw new ApiException(HttpStatus.BAD_REQUEST, "Bitiş saati başlangıç saatinden sonra olmalıdır.");
            if (previous != null && current.startTime().isBefore(previous.endTime()))
                throw new ApiException(HttpStatus.BAD_REQUEST, "Çalışma aralıkları çakışamaz.");
            previous = current;
        }
        if (!merge(sorted).equals(intersect(sorted, branchHours)))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Employee saatleri branch çalışma saatleri içinde olmalıdır.");
    }

    private List<EmployeeTimeIntervalDetails> intersect(List<EmployeeTimeIntervalDetails> employee, List<EmployeeTimeIntervalDetails> branch) {
        var result = new ArrayList<EmployeeTimeIntervalDetails>();
        for (var a : employee) for (var b : branch) {
            var start = a.startTime().isAfter(b.startTime()) ? a.startTime() : b.startTime();
            var end = a.endTime().isBefore(b.endTime()) ? a.endTime() : b.endTime();
            if (start.isBefore(end)) result.add(new EmployeeTimeIntervalDetails(start, end));
        }
        return merge(result);
    }

    private List<EmployeeTimeIntervalDetails> merge(List<EmployeeTimeIntervalDetails> intervals) {
        var result = new ArrayList<EmployeeTimeIntervalDetails>();
        for (var next : intervals.stream().sorted(Comparator.comparing(EmployeeTimeIntervalDetails::startTime)).toList()) {
            if (result.isEmpty() || result.getLast().endTime().isBefore(next.startTime())) result.add(next);
            else {
                var last = result.removeLast();
                result.add(new EmployeeTimeIntervalDetails(last.startTime(), last.endTime().isAfter(next.endTime()) ? last.endTime() : next.endTime()));
            }
        }
        return List.copyOf(result);
    }
}
