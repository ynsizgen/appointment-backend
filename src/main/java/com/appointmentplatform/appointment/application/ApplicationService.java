package com.appointmentplatform.appointment.application;

import com.appointmentplatform.appointment.AppointmentStatus;

import com.appointmentplatform.appointment.AppointmentSource;
import com.appointmentplatform.employee.EmployeeDetails;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import com.appointmentplatform.appointment.domain.Appointment;
import com.appointmentplatform.appointment.persistence.AppointmentRepository;
import com.appointmentplatform.appointment.web.dto.AppointmentCreateRequest;
import com.appointmentplatform.branch.BranchOperations;
import com.appointmentplatform.brand.BrandOperations;
import com.appointmentplatform.identity.SubscriptionOperations;
import com.appointmentplatform.identity.PhoneVerifications;
import com.appointmentplatform.employee.EmployeeDailyScheduleDetails;
import com.appointmentplatform.employee.EmployeeOperations;
import com.appointmentplatform.employee.EmployeeScheduleOperations;
import com.appointmentplatform.notification.AppointmentSmsJob;
import com.appointmentplatform.notification.AppointmentSmsJobs;
import com.appointmentplatform.notification.SmsJobType;
import com.appointmentplatform.service.ServiceDetails;
import com.appointmentplatform.service.ServiceOperations;
import com.appointmentplatform.shared.ApiException;
import com.appointmentplatform.shared.Crypto;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ApplicationService{

    private final EmployeeScheduleOperations employeeScheduleOperations;
    private final EmployeeOperations employeeOperations;
    private final ServiceOperations serviceOperations;
    private final Clock clock;
    private final AppointmentRepository appointmentRepository;
    private final BranchOperations branchOperations;
    private final SubscriptionOperations subscriptions;
    private final PhoneVerifications phoneVerifications;
    private final BrandOperations brands;
    private final Crypto crypto;
    private final AppointmentSmsJobs appointmentSmsJobs;
    private final String publicUrl;

    public ApplicationService(
            EmployeeScheduleOperations employeeScheduleOperations,
            EmployeeOperations employeeOperations,
            ServiceOperations serviceOperations,
            Clock clock,
            AppointmentRepository appointmentRepository,
            BranchOperations branchOperations,
            SubscriptionOperations subscriptions,
            PhoneVerifications phoneVerifications,
            BrandOperations brands,
            Crypto crypto,
            AppointmentSmsJobs appointmentSmsJobs,
            @Value("${app.public-url}") String publicUrl) {
        this.employeeScheduleOperations = employeeScheduleOperations;
        this.employeeOperations = employeeOperations;
        this.serviceOperations = serviceOperations;
        this.clock = clock;
        this.appointmentRepository = appointmentRepository;
        this.branchOperations = branchOperations;
        this.subscriptions = subscriptions;
        this.phoneVerifications = phoneVerifications;
        this.brands = brands;
        this.crypto = crypto;
        this.appointmentSmsJobs = appointmentSmsJobs;
        this.publicUrl = publicUrl;
    }


    public AvailableAppointmentSlotDetails getAvailableStartTimes(
            UUID brandId,
            UUID serviceId,
            UUID employeeId,
            LocalDate date) {

        serviceOperations.requireOwnedBy(serviceId, brandId);
        employeeOperations.requireServiceAssigned(brandId, employeeId, serviceId);

        serviceOperations.requireAssignedToBranch(
                serviceId,
                employeeOperations.branchId(employeeId)
        );

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
               startTime = startTime.plusMinutes(service.durationMinutes());
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
    public AppointmentDetails createByCustomer(UUID branchId, @Valid AppointmentCreateRequest request, String verificationToken) {
        phoneVerifications.consume(request.customerPhone(), verificationToken);
        return create(branchOperations.brandId(branchId), branchId, request, AppointmentSource.CUSTOMER);
    }


    private AppointmentDetails create(UUID brandId, UUID branchId, @Valid AppointmentCreateRequest request, AppointmentSource source) {

        if (!employeeOperations.branchId(request.employeeId()).equals(branchId)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Çalışan seçilen şubeye ait değil.");
        }

        subscriptions.requireActiveSubscription(brandId);
        branchOperations.requireAcceptingAppointments(branchId, brandId);

        employeeOperations.requireServiceAssigned(brandId, request.employeeId(), request.serviceId());
        serviceOperations.requireOwnedBy(request.serviceId(), brandId);

        serviceOperations.requireAssignedToBranch(
                request.serviceId(),
                branchId
        );

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

        boolean overlaps = appointmentRepository.existsOverlapping(
                request.employeeId(),
                startAt,
                endAt,
                List.of(AppointmentStatus.PENDING, AppointmentStatus.APPROVED));

        if (overlaps) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "Çalışanın bu saat aralığında başka bir randevusu var.");
        }


        Instant cancellationDeadline = startAt.minus(Duration.ofHours(brands.cancellationNoticeHours(brandId)));

        String accessToken= crypto.token();

        Instant accessTokenExpiresAt = startAt.plus(Duration.ofDays(30));

        Appointment appointment = appointmentRepository.saveAndFlush(new Appointment(
                UUID.randomUUID(),
                branchId,
                request.employeeId(),
                request.serviceId(),
                service.displayName(),
                service.durationMinutes(),
                request.customerFirstName(),
                request.customerLastName().strip(),
                request.customerPhone().strip(),
                email,
                startAt,
                endAt,
                AppointmentStatus.PENDING,
                source,
                clock.instant(),
                cancellationDeadline,
                Crypto.hash(accessToken),
                accessTokenExpiresAt
                ));

        appointmentSmsJobs.enqueue(new AppointmentSmsJob(appointment.getId(),
                appointment.getCustomerPhone(),
                "Randevunuz oluşturulmuştur." + publicUrl + "/randevu#token=" + accessToken, SmsJobType.APPOINTMENT_CREATED,clock.instant()));

        Instant startDate = appointment.getStartsAt();

        String reminderMessage = "Hatırlatma: Randevunuz "
                + startDate.atZone(ZoneId.of("Europe/Istanbul"))
                        .format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"))
                + " tarihinde.";

        Instant time2 = startDate.minus(Duration.ofHours(2));
        Instant time24 = startDate.minus(Duration.ofHours(24));

        if(!clock.instant().isAfter(time2)) {
            appointmentSmsJobs.enqueue(new AppointmentSmsJob(
                    appointment.getId(),
                    appointment.getCustomerPhone(),
                    reminderMessage,
                    SmsJobType.REMINDER_2_HOURS,
                    time2)
            );
        }

        if(!clock.instant().isAfter(time24)){
            appointmentSmsJobs.enqueue(new AppointmentSmsJob(
                    appointment.getId(),
                    appointment.getCustomerPhone(),
                    reminderMessage,
                    SmsJobType.REMINDER_24_HOURS,
                    time24)
            );
        }


        return new AppointmentDetails(
                appointment.getId(),
                appointment.getBranchId(),
                appointment.getEmployeeId(),
                appointment.getServiceId(),
                appointment.getServiceName(),
                appointment.getDurationMinutes(),
                appointment.getStartsAt(),
                appointment.getEndsAt(),
                appointment.getStatus(),
                appointment.getCancellationDeadline(),
                appointment.getCustomerPhone());

    }
    @Transactional
    @PreAuthorize("hasRole('BRAND') and #brandId == authentication.principal.brandId")
    public void approveAppointment(UUID brandId, UUID appointmentId) {

        Appointment appointment = appointmentRepository.findByIdForUpdate(appointmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Randevu bulunamadı."));

        branchOperations.requireOwnedBy(appointment.getBranchId(), brandId);

        if (appointment.getStatus() != AppointmentStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "Bu randevu artık onay beklemiyor.");
        }

        appointment.setStatus(AppointmentStatus.APPROVED);
    }

    @Transactional
    @PreAuthorize("hasRole('BRAND') and #brandId == authentication.principal.brandId")
    public void rejectAppointment(UUID brandId, UUID appointmentId) {
        Appointment appointment = appointmentRepository.findByIdForUpdate(appointmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Randevu bulunamadı."));

        branchOperations.requireOwnedBy(appointment.getBranchId(), brandId);

        if (appointment.getStatus() != AppointmentStatus.PENDING) {
            throw new ApiException(HttpStatus.CONFLICT, "Bu randevu artık onay beklemiyor.");
        }

        appointment.setStatus(AppointmentStatus.REJECTED);

        appointmentSmsJobs.enqueue(new AppointmentSmsJob(
                appointment.getId(),
                appointment.getCustomerPhone(),
                "Randevu talebiniz işletme tarafından reddedilmiştir.",
                SmsJobType.APPOINTMENT_REJECTED,
                clock.instant())
        );
    }
    @Transactional
    @PreAuthorize("hasRole('BRAND') and #brandId == authentication.principal.brandId")
    public void cancelAppointment(UUID brandId, UUID appointmentId) {
        Appointment appointment = appointmentRepository.findByIdForUpdate(appointmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Randevu bulunamadı."));

        branchOperations.requireOwnedBy(appointment.getBranchId(), brandId);

        if (appointment.getStatus() != AppointmentStatus.PENDING
                && appointment.getStatus() != AppointmentStatus.APPROVED) {
            throw new ApiException(HttpStatus.CONFLICT, "Yalnızca bekleyen veya onaylı randevular iptal edilebilir.");
        }

        appointment.setStatus(AppointmentStatus.CANCELLED);

        appointmentSmsJobs.enqueue(new AppointmentSmsJob(
                appointment.getId(),
                appointment.getCustomerPhone(),
                        "Randevunuz işletme tarafından iptal edilmiştir.",
                SmsJobType.APPOINTMENT_CANCELLED,
                clock.instant())
                );
    }

    public CustomerAppointmentDetails appointmentDetails(String token) {
       Appointment appointment = appointmentRepository.findAllByAccessTokenHash(Crypto.hash(token)).orElseThrow(()-> new ApiException(HttpStatus.NOT_FOUND,"Appointment bulunamadı."));

       if (!appointment.getAccessTokenExpiresAt().isAfter(clock.instant())) {
           throw new ApiException(HttpStatus.GONE, "Bağlantının süresi dolmuş.");
       }

       return new CustomerAppointmentDetails(
               appointment.getServiceName(),
               appointment.getDurationMinutes(),
               appointment.getCustomerFirstName(),
               appointment.getCustomerLastName(),
               appointment.getCustomerPhone(),
               appointment.getCustomerEmail(),
               appointment.getStartsAt(),
               appointment.getStatus(),
               appointment.getAccessTokenExpiresAt());
    }

    @Transactional
    public void cancelAppointmentByCustomer(String token) {
        Appointment appointment = appointmentRepository.findByAccessTokenHashForUpdate(Crypto.hash(token))
                .orElseThrow(()-> new ApiException(HttpStatus.NOT_FOUND,"Appointment bulunamadı."));


        if(!appointment.getAccessTokenExpiresAt().isAfter(clock.instant())){
            throw new ApiException(HttpStatus.GONE,"Appointment token süresi dolmuş.");
        }
        if (appointment.getStatus() != AppointmentStatus.PENDING && appointment.getStatus() != AppointmentStatus.APPROVED) {
            throw new ApiException(HttpStatus.CONFLICT, "Yalnızca bekleyen veya onaylı randevular iptal edilebilir.");
        }
        if(!appointment.getCancellationDeadline().isAfter(clock.instant())){
            throw new ApiException(HttpStatus.CONFLICT,"Randevunun iptal süresi dolmuş.");
        }


        appointment.setStatus(AppointmentStatus.CANCELLED);

        appointmentSmsJobs.enqueue(new AppointmentSmsJob(
                appointment.getId(),
                appointment.getCustomerPhone(),
                "Randevu iptal edilmiştir.",
                SmsJobType.APPOINTMENT_CANCELLED,
                clock.instant())
        );
    }

    @PreAuthorize("hasRole('BRAND') and #brandId == authentication.principal.brandId")
    public List<DailyBranchAppointmentsDetails> showDailyAppointments(UUID brandId,UUID branchId, LocalDate date) {

        ZoneId zone = ZoneId.of("Europe/Istanbul");
        branchOperations.requireOwnedBy(branchId, brandId);


        Instant start = date.atStartOfDay().atZone(zone).toInstant();
        Instant end = date.plusDays(1).atStartOfDay().atZone(zone).toInstant();

        List<Appointment> appointments = appointmentRepository.findByBranchIdAndDate(branchId,start,end);
        List<EmployeeDetails> employees = employeeOperations.findAllEmployeeByBranchId(branchId);

        Map<UUID,EmployeeDetails> employeesById = employees.stream()
                .collect(Collectors.toMap(EmployeeDetails::id, employee -> employee));

        return appointments.stream()
                .map(appointment -> {
                    EmployeeDetails employee =
                            employeesById.get(appointment.getEmployeeId());

                    return new DailyBranchAppointmentsDetails(
                            appointment.getId(),
                            appointment.getBranchId(),
                            appointment.getEmployeeId(),
                            employee == null ? null : employee.firstName(),
                            employee == null ? null : employee.lastName(),
                            appointment.getServiceName(),
                            appointment.getDurationMinutes(),
                            appointment.getCustomerFirstName(),
                            appointment.getCustomerLastName(),
                            appointment.getCustomerPhone(),
                            appointment.getStartsAt(),
                            appointment.getEndsAt(),
                            appointment.getStatus(),
                            appointment.getSource()
                    );
                })
                .toList();
    }
}
