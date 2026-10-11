package com.appointmentplatform.employee.application;

import com.appointmentplatform.branch.BranchOperations;
import com.appointmentplatform.employee.domain.*;
import com.appointmentplatform.employee.persistence.*;
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
@PreAuthorize("hasRole('BRAND') and #brandId == authentication.principal.brandId")
public class EmployeeScheduleService {
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

    public EmployeeWeeklyScheduleDetails weekly(UUID brandId, UUID employeeId) {
        var employee = owned(brandId, employeeId, false);
        return weeklyConfiguration(brandId, employee);
    }

    @Transactional
    public void replaceWeekly(UUID brandId, UUID employeeId, EmployeeWeeklyScheduleRequest request) {
        var employee = owned(brandId, employeeId, true);
        var branchHours = branchHours(brandId, employee);
        for (var day : DayOfWeek.values()) {
            var intervals = request.workingHours().stream().filter(h -> h.dayOfWeek() == day)
                    .map(h -> new EmployeeTimeIntervalDetails(h.startTime(), h.endTime())).toList();
            validate(intervals, forDay(branchHours, day));
        }
        var schedule = weekly.findById(employeeId).orElseGet(() -> new EmployeeWeeklySchedule(employeeId));
        schedule.replace(request.workingHours().stream()
                .map(h -> new EmployeeWeeklyInterval(h.dayOfWeek(), h.startTime(), h.endTime())).toList());
        weekly.save(schedule);
    }

    @Transactional
    public void resetWeekly(UUID brandId, UUID employeeId) {
        owned(brandId, employeeId, true);
        weekly.findById(employeeId).ifPresent(weekly::delete);
    }

    public EmployeeDailyScheduleDetails daily(UUID brandId, UUID employeeId, LocalDate date) {
        var employee = owned(brandId, employeeId, false);
        var branch = branchHours(brandId, employee);
        var override = overrides.findByEmployeeIdAndDate(employeeId, date);
        List<EmployeeTimeIntervalDetails> configured;
        String source;
        if (override.isPresent()) {
            configured = override.get().intervals().stream().map(h -> new EmployeeTimeIntervalDetails(h.startTime(), h.endTime())).toList();
            source = "DATE_OVERRIDE";
        } else {
            var schedule = weeklyConfiguration(brandId, employee);
            configured = forDay(schedule.workingHours(), date.getDayOfWeek());
            source = schedule.usesBranchHours() ? "BRANCH" : "EMPLOYEE_WEEKLY";
        }
        configured = merge(configured);
        var effective = employee.isActive() ? intersect(configured, forDay(branch, date.getDayOfWeek())) : List.<EmployeeTimeIntervalDetails>of();
        return new EmployeeDailyScheduleDetails(date, source, employee.isActive(), configured, effective);
    }

    @Transactional
    public void replaceDate(UUID brandId, UUID employeeId, LocalDate date, EmployeeDateScheduleRequest request) {
        var employee = owned(brandId, employeeId, true);
        requireEditable(date);
        var intervals = request.workingHours().stream().map(h -> new EmployeeTimeIntervalDetails(h.startTime(), h.endTime())).toList();
        validate(intervals, forDay(branchHours(brandId, employee), date.getDayOfWeek()));
        var override = overrides.findByEmployeeIdAndDate(employeeId, date)
                .orElseGet(() -> new EmployeeScheduleOverride(employeeId, date));
        override.replace(intervals.stream().map(h -> new EmployeeTimeInterval(h.startTime(), h.endTime())).toList());
        overrides.save(override);
    }

    @Transactional
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

    private EmployeeWeeklyScheduleDetails weeklyConfiguration(
            UUID brandId, Employee employee) {

        var schedule = weekly.findById(employee.getId());

        if (schedule.isEmpty()) {
            var hours = branchHours(brandId, employee);
            return new EmployeeWeeklyScheduleDetails(true, hours);
        }

        var hours = schedule.get().intervals().stream()
                .map(h -> new EmployeeWeeklyIntervalDetails(
                        h.dayOfWeek(),
                        h.startTime(),
                        h.endTime()
                ))
                .sorted(
                        Comparator.comparing(EmployeeWeeklyIntervalDetails::dayOfWeek)
                                .thenComparing(EmployeeWeeklyIntervalDetails::startTime)
                )
                .toList();

        return new EmployeeWeeklyScheduleDetails(false, hours);
    }

    private List<EmployeeWeeklyIntervalDetails> branchHours(UUID brandId, Employee employee) {
        return branches.weeklyHours(employee.getBranchId(), brandId).stream()
                .map(h -> new EmployeeWeeklyIntervalDetails(h.dayOfWeek(), h.startTime(), h.endTime()))
                .sorted(Comparator.comparing(EmployeeWeeklyIntervalDetails::dayOfWeek).thenComparing(EmployeeWeeklyIntervalDetails::startTime)).toList();
    }

    private List<EmployeeTimeIntervalDetails> forDay(List<EmployeeWeeklyIntervalDetails> hours, DayOfWeek day) {
        return hours.stream().filter(h -> h.dayOfWeek() == day).map(h -> new EmployeeTimeIntervalDetails(h.startTime(), h.endTime())).toList();
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
