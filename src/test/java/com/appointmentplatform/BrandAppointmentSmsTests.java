package com.appointmentplatform;

import com.appointmentplatform.appointment.AppointmentSource;
import com.appointmentplatform.appointment.AppointmentStatus;
import com.appointmentplatform.appointment.domain.Appointment;
import com.appointmentplatform.appointment.persistence.AppointmentRepository;
import com.appointmentplatform.brand.domain.Brand;
import com.appointmentplatform.brand.persistence.BrandRepository;
import com.appointmentplatform.branch.domain.Branch;
import com.appointmentplatform.branch.persistence.BranchRepository;
import com.appointmentplatform.employee.domain.Employee;
import com.appointmentplatform.employee.persistence.EmployeeRepository;
import com.appointmentplatform.notification.SmsJobType;
import com.appointmentplatform.notification.application.NotificationSmsService;
import com.appointmentplatform.notification.persistence.SmsJobRepository;
import com.appointmentplatform.service.domain.Service;
import com.appointmentplatform.service.persistence.ServicesRepository;
import com.appointmentplatform.shared.Crypto;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.appointmentplatform.appointment.application.ApplicationService;
import com.appointmentplatform.identity.security.AccountPrincipal;
import com.appointmentplatform.shared.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doAnswer;

import com.appointmentplatform.notification.domain.SmsJob;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class BrandAppointmentSmsTests {
    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");
    @Autowired
    BrandRepository brands;
    @Autowired
    BranchRepository branches;
    @Autowired
    EmployeeRepository employees;
    @Autowired
    ServicesRepository services;
    @Autowired
    AppointmentRepository appointments;
    @Autowired
    SmsJobRepository smsJobs;
    @Autowired
    Crypto crypto;
    @Autowired
    ApplicationService application;
    @MockitoBean
    Clock clock;
    @MockitoSpyBean
    NotificationSmsService notification;

    record Booking(Appointment appointment, String token) {}

    @BeforeEach
    void setup() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private Booking booking(AppointmentStatus status, Instant deadline, Instant expiry) {
        UUID brand = UUID.randomUUID();
        UUID branch = UUID.randomUUID();
        UUID employee = UUID.randomUUID();
        UUID service = UUID.randomUUID();
        brands.saveAndFlush(new Brand(brand, "Test Brand"));
        branches.saveAndFlush(new Branch(branch, brand, "Branch", "Address", null));
        employees.saveAndFlush(new Employee(employee, branch, "Ada", "Lovelace", true));
        services.saveAndFlush(new Service(service, brand, "Cut", 60));
        String token = crypto.token();
        var appointment = appointments.saveAndFlush(new Appointment(
                UUID.randomUUID(), branch, employee, service, "Cut", 60,
                "Ada", "Lovelace", "+905551112233", null,
                NOW.plusSeconds(86400), NOW.plusSeconds(90000), status,
                AppointmentSource.CUSTOMER, NOW, deadline, Crypto.hash(token), expiry));
        return new Booking(appointment, token);
    }

    private Booking booking(AppointmentStatus status) {
        return booking(status, NOW.plusSeconds(3600), NOW.plusSeconds(31L * 86400));
    }


    private UUID owner(Booking booking) {
        return branches.findById(booking.appointment().getBranchId()).orElseThrow().brandId();
    }

    private void authenticate(UUID brandId) {
        var principal = new AccountPrincipal(UUID.randomUUID(), "BRAND", brandId, NOW);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, AuthorityUtils.createAuthorityList("ROLE_BRAND"));
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private void decide(String action, UUID brandId, UUID appointmentId) {
        authenticate(brandId);
        if (action.equals("reject")) {
            application.rejectAppointment(brandId, appointmentId);
        } else {
            application.cancelAppointment(brandId, appointmentId);
        }
    }

    private List<SmsJob> jobs(UUID id) {
        return smsJobs.findAll().stream().filter(job -> job.appointmentId().equals(id)).toList();
    }

    @ParameterizedTest
    @CsvSource({"reject,PENDING,REJECTED,APPOINTMENT_REJECTED", "cancel,PENDING,CANCELLED,APPOINTMENT_CANCELLED", "cancel,APPROVED,CANCELLED,APPOINTMENT_CANCELLED"})
    void decisionQueuesOneEncryptedSmsAndRepeatConflicts(String action, AppointmentStatus initial,
            AppointmentStatus expected, SmsJobType type) {
        var booking = booking(initial);
        UUID id = booking.appointment().getId();
        UUID brandId = owner(booking);
        decide(action, brandId, id);
        assertThat(appointments.findById(id).orElseThrow().getStatus()).isEqualTo(expected);
        assertThat(jobs(id)).hasSize(1);
        var job = jobs(id).getFirst();
        String message = action.equals("reject")
                ? "Randevu talebiniz işletme tarafından reddedilmiştir."
                : "Randevunuz işletme tarafından iptal edilmiştir.";
        assertThat(job.type()).isEqualTo(type);
        assertThat(job.recipient()).isEqualTo(booking.appointment().getCustomerPhone());
        assertThat(job.encryptedBody()).doesNotContain(message);
        assertThat(crypto.decrypt(job.encryptedBody())).isEqualTo(message);
        assertThat(job.nextAttemptAt()).isEqualTo(NOW);
        assertThatThrownBy(() -> decide(action, brandId, id))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(jobs(id)).hasSize(1);
    }

    @ParameterizedTest
    @CsvSource({"reject,PENDING", "cancel,PENDING", "cancel,APPROVED"})
    void failureAfterSmsInsertRollsBackBothStatusAndSms(String action, AppointmentStatus initial) {
        var booking = booking(initial);
        UUID id = booking.appointment().getId();
        NotificationSmsService target = AopTestUtils.getUltimateTargetObject(notification);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("Failure after queue insert");
        }).when(target).enqueue(any());
        assertThatThrownBy(() -> decide(action, owner(booking), id)).isInstanceOf(IllegalStateException.class);
        assertThat(appointments.findById(id).orElseThrow().getStatus()).isEqualTo(initial);
        assertThat(jobs(id)).isEmpty();
    }

    @ParameterizedTest
    @CsvSource({"reject", "cancel"})
    void anotherBrandCannotChangeStatusOrQueueSms(String action) {
        var booking = booking(AppointmentStatus.PENDING);
        UUID id = booking.appointment().getId();
        assertThatThrownBy(() -> decide(action, UUID.randomUUID(), id))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.status()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThat(appointments.findById(id).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.PENDING);
        assertThat(jobs(id)).isEmpty();
    }

    @Test
    void concurrentRejectAndCancelQueueOnlyWinningDecision() throws Exception {
        var booking = booking(AppointmentStatus.PENDING);
        UUID id = booking.appointment().getId();
        UUID brandId = owner(booking);
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tasks = List.of("reject", "cancel").stream().map(action -> executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                try {
                    decide(action, brandId, id);
                    return 200;
                } catch (ApiException e) {
                    return e.status().value();
                } finally {
                    SecurityContextHolder.clearContext();
                }
            })).toList();
            assertThat(List.of(tasks.get(0).get(20, TimeUnit.SECONDS), tasks.get(1).get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        var status = appointments.findById(id).orElseThrow().getStatus();
        assertThat(jobs(id)).hasSize(1);
        assertThat(jobs(id).getFirst().type()).isEqualTo(status == AppointmentStatus.REJECTED
                ? SmsJobType.APPOINTMENT_REJECTED : SmsJobType.APPOINTMENT_CANCELLED);
    }
}
