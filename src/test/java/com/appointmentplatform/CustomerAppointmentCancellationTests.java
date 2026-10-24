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
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CustomerAppointmentCancellationTests {
    private static final Instant NOW = Instant.parse("2030-01-01T10:00:00Z");
    private static final String CANCEL = "/api/public/appointment/cancel-appointment";
    @LocalServerPort
    int port;
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
    ObjectMapper json;
    @MockitoBean
    Clock clock;
    @MockitoSpyBean
    NotificationSmsService notification;

    private HttpClient client;
    private String csrfHeader;
    private String csrfToken;

    record Booking(Appointment appointment, String token) {}

    @BeforeEach
    void setup() throws Exception {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        client = HttpClient.newBuilder().cookieHandler(new CookieManager()).build();
        var response = client.send(HttpRequest.newBuilder(uri("/api/auth/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        var csrf = json.readTree(response.body());
        csrfHeader = csrf.get("headerName").asText();
        csrfToken = csrf.get("token").asText();
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

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + path);
    }

    private HttpResponse<String> cancel(String token, boolean csrf) throws Exception {
        var builder = HttpRequest.newBuilder(uri(CANCEL)).POST(HttpRequest.BodyPublishers.noBody());
        if (token != null) {
            builder.header("X-Appointment-Token", token);
        }
        if (csrf) {
            builder.header(csrfHeader, csrfToken);
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private long cancellationSmsCount(UUID id) {
        return smsJobs.findAll().stream()
                .filter(job -> job.appointmentId().equals(id) && job.type() == SmsJobType.APPOINTMENT_CANCELLED)
                .count();
    }

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = {"PENDING", "APPROVED"})
    void validAnonymousCancellationFreesTimeAndQueuesOneEncryptedSms(AppointmentStatus status) throws Exception {
        var booking = booking(status);
        var appointment = booking.appointment();
        var details = client.send(HttpRequest.newBuilder(uri("/api/public/appointment/details"))
                .header("X-Appointment-Token", booking.token()).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(details.statusCode()).isEqualTo(200);
        assertThat(details.body()).doesNotContain(booking.token(), appointment.getAccessTokenHash());
        assertThat(appointments.findById(appointment.getId()).orElseThrow().getStatus()).isEqualTo(status);

        var response = cancel(booking.token(), true);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(appointments.findById(appointment.getId()).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(appointments.findBlockingInRange(appointment.getEmployeeId(), appointment.getStartsAt(),
                appointment.getEndsAt(), List.of(AppointmentStatus.PENDING, AppointmentStatus.APPROVED))).isEmpty();
        assertThat(cancellationSmsCount(appointment.getId())).isEqualTo(1);
        var sms = smsJobs.findAll().stream().filter(job -> job.appointmentId().equals(appointment.getId())).findFirst().orElseThrow();
        assertThat(sms.encryptedBody()).doesNotContain("Randevu iptal");
        assertThat(crypto.decrypt(sms.encryptedBody())).isEqualTo("Randevu iptal edilmiştir.");
        assertThat(cancel(booking.token(), true).statusCode()).isEqualTo(409);
        assertThat(cancellationSmsCount(appointment.getId())).isEqualTo(1);
    }

    @Test
    void expiredOrUnknownTokenAndMissingCsrfCannotCancel() throws Exception {
        var valid = booking(AppointmentStatus.PENDING);
        assertThat(cancel("unknown-token", true).statusCode()).isEqualTo(404);
        assertThat(cancel(null, true).statusCode()).isEqualTo(400);
        assertThat(cancel(valid.token(), false).statusCode()).isEqualTo(403);
        var expired = booking(AppointmentStatus.PENDING, NOW.minusSeconds(1), NOW);
        assertThat(cancel(expired.token(), true).statusCode()).isEqualTo(410);
        assertThat(appointments.findById(valid.appointment().getId()).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.PENDING);
        assertThat(appointments.findById(expired.appointment().getId()).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.PENDING);
        assertThat(cancellationSmsCount(expired.appointment().getId())).isZero();
    }

    @Test
    void deadlineBoundaryRejectsAtDeadlineButAllowsOneInstantBefore() throws Exception {
        for (Instant deadline : List.of(NOW.minusSeconds(1), NOW)) {
            var booking = booking(AppointmentStatus.PENDING, deadline, NOW.plusSeconds(86400));
            assertThat(cancel(booking.token(), true).statusCode()).isEqualTo(409);
            assertThat(appointments.findById(booking.appointment().getId()).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.PENDING);
            assertThat(cancellationSmsCount(booking.appointment().getId())).isZero();
        }
        var allowed = booking(AppointmentStatus.PENDING, NOW.plusMillis(1), NOW.plusSeconds(86400));
        assertThat(cancel(allowed.token(), true).statusCode()).isEqualTo(200);
    }

    @ParameterizedTest
    @EnumSource(value = AppointmentStatus.class, names = {"REJECTED", "CANCELLED"})
    void terminalStatusCannotBeCancelledAgain(AppointmentStatus status) throws Exception {
        var booking = booking(status);
        assertThat(cancel(booking.token(), true).statusCode()).isEqualTo(409);
        assertThat(appointments.findById(booking.appointment().getId()).orElseThrow().getStatus()).isEqualTo(status);
        assertThat(cancellationSmsCount(booking.appointment().getId())).isZero();
    }

    @Test
    void simultaneousRequestsProduceOneCancellationAndOneSms() throws Exception {
        var booking = booking(AppointmentStatus.PENDING);
        var barrier = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return cancel(booking.token(), true).statusCode();
            });
            var second = executor.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return cancel(booking.token(), true).statusCode();
            });
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(cancellationSmsCount(booking.appointment().getId())).isEqualTo(1);
    }

    @Test
    void enqueueFailureRollsBackCancellation() throws Exception {
        var booking = booking(AppointmentStatus.PENDING);
        NotificationSmsService target = AopTestUtils.getUltimateTargetObject(notification);
        doThrow(new IllegalStateException("Queue unavailable")).when(target).enqueue(any());
        assertThat(cancel(booking.token(), true).statusCode()).isEqualTo(500);
        assertThat(appointments.findById(booking.appointment().getId()).orElseThrow().getStatus()).isEqualTo(AppointmentStatus.PENDING);
        assertThat(cancellationSmsCount(booking.appointment().getId())).isZero();
    }
}
