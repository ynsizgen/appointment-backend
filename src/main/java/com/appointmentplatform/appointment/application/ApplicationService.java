package com.appointmentplatform.appointment.application;

import com.appointmentplatform.appointment.AppointmentStatus;

import com.appointmentplatform.appointment.AppointmentSource;


import com.appointmentplatform.appointment.domain.Appointment;
import com.appointmentplatform.appointment.persistence.AppointmentRepository;
import com.appointmentplatform.appointment.web.dto.AppointmentCreateRequest;
import com.appointmentplatform.branch.BranchOperations;
import com.appointmentplatform.employee.EmployeeDailyScheduleDetails;
import com.appointmentplatform.employee.EmployeeOperations;
import com.appointmentplatform.employee.EmployeeScheduleOperations;
import com.appointmentplatform.employee.EmployeeTimeIntervalDetails;
import com.appointmentplatform.service.ServiceDetails;
import com.appointmentplatform.service.ServiceOperations;
import com.appointmentplatform.shared.ApiException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class ApplicationService {

    private final EmployeeScheduleOperations employeeScheduleOperations;
    private final EmployeeOperations employeeOperations;
    private final ServiceOperations serviceOperations;
    private final Clock clock;
    private final AppointmentRepository appointmentRepository;
    private final BranchOperations branchOperations;

    public ApplicationService(EmployeeScheduleOperations employeeScheduleOperations, EmployeeOperations employeeOperations, ServiceOperations serviceOperations, Clock clock, AppointmentRepository appointmentRepository, BranchOperations branchOperations) {
        this.employeeScheduleOperations = employeeScheduleOperations;
        this.employeeOperations = employeeOperations;
        this.serviceOperations = serviceOperations;
        this.clock = clock;
        this.appointmentRepository = appointmentRepository;
        this.branchOperations = branchOperations;
    }


    public AvailableAppointmentSlotDetails getAvailableStartTimes(
            UUID brandId,
            UUID serviceId,
            UUID employeeId,
            LocalDate date) {

        serviceOperations.requireOwnedBy(serviceId, brandId);
        employeeOperations.requireServiceAssigned(brandId, employeeId, serviceId);


        var service = serviceOperations.findById(serviceId);

        var schedule = employeeScheduleOperations.daily(brandId,employeeId,date);

        ZoneId zone = ZoneId.of("Europe/Istanbul");


        Instant rangeStart = date.atStartOfDay(zone).toInstant();
        Instant rangeEnd = date.plusDays(1).atStartOfDay(zone).toInstant();



        record DateRange(LocalDateTime start, LocalDateTime end) {}var appointments = appointmentRepository.findBlockingInRange(employeeId, rangeStart, rangeEnd,
                        List.of(AppointmentStatus.PENDING, AppointmentStatus.APPROVED))
                .stream()
                .map(a -> new DateRange(a.getStartsAt().atZone(zone).toLocalDateTime(),
                        a.getEndsAt().atZone(zone).toLocalDateTime()))
                .toList();

        List<DateRange> reminders = new ArrayList<>();

        for (var hour : schedule.workingHours()) {

            LocalDateTime startHour = date.atTime(hour.startTime());
            LocalDateTime endHour = date.atTime(hour.endTime());

            for (var app : appointments) {
                if (!(startHour.isBefore(app.end())
                        && endHour.isAfter(app.start()))) {
                    continue;
                }

                // Randevudan önceki boşluk.
                if (startHour.isBefore(app.start())) {
                    reminders.add(new DateRange(startHour, app.start()));
                }

                // Kaldığımız yeri ilerlet.
                if (app.end().isAfter(startHour)) {
                    startHour = app.end();
                }
            }

            // Bütün randevular bittikten sonra kalan boşluk.
            if (startHour.isBefore(endHour)) {
                reminders.add(new DateRange(startHour, endHour));
            }
        }

        List<LocalTime> slot = new ArrayList<>();
        LocalDateTime earliestStart = LocalDateTime.now(clock.withZone(zone)).plusHours(1);

        for(var time : reminders){
            LocalDateTime startTime = time.start();
            LocalDateTime endTime = time.end();


            while (!startTime.plusMinutes(service.durationMinutes()).isAfter(endTime)) {
                if(!startTime.isBefore(earliestStart))
                    slot.add(startTime.toLocalTime());
               startTime= startTime.plusMinutes(service.durationMinutes());
            }

        }

        return new AvailableAppointmentSlotDetails(serviceId,employeeId,slot);
    }

    @Transactional
    @PreAuthorize("hasRole('BRAND') and #brandId == authentication.principal.brandId")
    public AppointmentDetails createByBrand(UUID brandId, @Valid AppointmentCreateRequest request) {


        return create(brandId, employeeOperations.branchId(request.employeeId()), request, AppointmentSource.BRAND);
    }

    @Transactional
    public AppointmentDetails createByCustomer(UUID branchId, @Valid AppointmentCreateRequest request) {
        // TODO  SMS doğrulaması

        return create(branchOperations.brandId(branchId), branchId, request, AppointmentSource.CUSTOMER);
    }


    private AppointmentDetails create(UUID brandId, UUID branchId, @Valid AppointmentCreateRequest request, AppointmentSource source) {

        if (!employeeOperations.branchId(request.employeeId()).equals(branchId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Çalışan seçilen şubeye ait değil.");
        }

        employeeOperations.requireServiceAssigned(brandId, request.employeeId(), request.serviceId());
        serviceOperations.requireOwnedBy(request.serviceId(), brandId);

        EmployeeDailyScheduleDetails employeeDailyScheduleDetails = employeeScheduleOperations.daily(brandId,request.employeeId(),request.date());

        if(!employeeDailyScheduleDetails.active()){
            throw new ApiException(HttpStatus.BAD_REQUEST,"Çalışan şuan uygun değil.");
        }



        ServiceDetails service = serviceOperations.findById(request.serviceId());

        LocalDateTime start = request.date().atTime(request.startTime());
        LocalDateTime end = start.plusMinutes(service.durationMinutes());

        LocalDateTime earliestStart = LocalDateTime
                .now(clock.withZone(ZoneId.of("Europe/Istanbul")))
                .plusHours(1);

        if (start.isBefore(earliestStart) && source==AppointmentSource.CUSTOMER) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Randevu en az 1 saat sonrası için oluşturulabilir.");
        }

        boolean fitsWorkingHours = employeeDailyScheduleDetails.workingHours()
                .stream()
                .anyMatch(hours ->
                        !start.isBefore(request.date().atTime(hours.startTime())) && !end.isAfter(request.date().atTime(hours.endTime())));

        if (!fitsWorkingHours) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Randevu çalışma saatlerinin içine sığmıyor.");
        }

        String email = request.customerEmail() == null ? null : request.customerEmail().strip();

        ZoneId zone = ZoneId.of("Europe/Istanbul");
        Instant startAt = start.atZone(zone).toInstant();
        Instant endAt = end.atZone(zone).toInstant();

        Appointment appointment = appointmentRepository.saveAndFlush(new Appointment(UUID.randomUUID(), branchId,  request.employeeId(), request.serviceId(),
                service.displayName(), service.durationMinutes(), request.customerFirstName(), request.customerLastName().strip(), request.customerPhone().strip(),
                email, startAt, endAt, AppointmentStatus.PENDING, source ,clock.instant()));

        return new AppointmentDetails(appointment.getId(),appointment.getBranchId(),appointment.getEmployeeId(), appointment.getServiceId(),appointment.getServiceName(), appointment.getDurationMinutes(),appointment.getStartsAt(),appointment.getEndsAt(),appointment.getStatus());

    }
}
