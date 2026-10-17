package com.appointmentplatform;

import com.appointmentplatform.appointment.AppointmentSource;
import com.appointmentplatform.appointment.AppointmentStatus;
import com.appointmentplatform.appointment.domain.Appointment;
import com.appointmentplatform.appointment.persistence.AppointmentRepository;
import com.appointmentplatform.branch.domain.Branch;
import com.appointmentplatform.branch.persistence.BranchRepository;
import com.appointmentplatform.brand.domain.Brand;
import com.appointmentplatform.brand.persistence.BrandRepository;
import com.appointmentplatform.employee.domain.Employee;
import com.appointmentplatform.employee.persistence.EmployeeRepository;
import com.appointmentplatform.service.domain.Service;
import com.appointmentplatform.service.persistence.ServicesRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AppointmentConcurrencyTests {
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
    PlatformTransactionManager transactions;

    record Fixture(UUID branch, UUID employee, UUID service) {
    }

    private Fixture fixture() {
        UUID brand = UUID.randomUUID();
        UUID branch = UUID.randomUUID();
        UUID employee = UUID.randomUUID();
        UUID service = UUID.randomUUID();
        brands.saveAndFlush(new Brand(brand, "Concurrency"));
        branches.saveAndFlush(new Branch(branch, brand, "Branch", "Address", null));
        employees.saveAndFlush(new Employee(employee, branch, "Ada", "Lovelace", true));
        services.saveAndFlush(new Service(service, brand, "Service", 60));
        return new Fixture(branch, employee, service);
    }

    private Appointment booking(Fixture f, int startMinute, int endMinute, AppointmentStatus status) {
        Instant base = Instant.parse("2030-01-01T07:00:00Z");
        return new Appointment(
                UUID.randomUUID(),
                f.branch(),
                f.employee(),
                f.service(),
                "Service",
                endMinute - startMinute,
                "Ada",
                "Lovelace",
                "+905551112233",
                null,
                base.plusSeconds(startMinute * 60L),
                base.plusSeconds(endMinute * 60L),
                status,
                AppointmentSource.CUSTOMER,
                Instant.now(),
                base.plusSeconds(startMinute * 60L));
    }

    @Test
    void databaseRejectsOneWriterEvenWhenBothPrechecksSeeAnEmptySlot() throws Exception {
        var f = fixture();
        var barrier = new CyclicBarrier(2);
        Callable<String> work = () -> {
            try {
                new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                    var a = booking(f, 0, 60, AppointmentStatus.PENDING);
                    assertThat(appointments.existsOverlapping(f.employee(), a.getStartsAt(), a.getEndsAt(),
                            List.of(AppointmentStatus.PENDING, AppointmentStatus.APPROVED))).isFalse();
                    try {
                        barrier.await(10, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                    appointments.saveAndFlush(a);
                });
                return "CREATED";
            } catch (DataIntegrityViolationException e) {
                assertThat(e.getMostSpecificCause().getMessage()).contains("ex_appointments_employee_time");
                return "CONFLICT";
            }
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(work);
            var second = executor.submit(work);
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("CREATED", "CONFLICT");
        }
        var base = Instant.parse("2030-01-01T07:00:00Z");
        assertThat(appointments.findBlockingInRange(f.employee(), base, base.plusSeconds(3600),
                List.of(AppointmentStatus.PENDING, AppointmentStatus.APPROVED))).hasSize(1);
    }

    @Test
    void rejectedAppointmentRemainsStoredButReleasesItsTimeRange() {
        var fixture = fixture();
        var original = appointments.saveAndFlush(booking(fixture, 0, 60, AppointmentStatus.PENDING));

        original.setStatus(AppointmentStatus.REJECTED);
        appointments.saveAndFlush(original);

        assertThat(appointments.findBlockingInRange(
                fixture.employee(),
                original.getStartsAt(),
                original.getEndsAt(),
                List.of(AppointmentStatus.PENDING, AppointmentStatus.APPROVED))).isEmpty();

        var replacement = appointments.saveAndFlush(booking(fixture, 0, 60, AppointmentStatus.PENDING));

        assertThat(appointments.findById(original.getId()).orElseThrow().getStatus())
                .isEqualTo(AppointmentStatus.REJECTED);
        assertThat(appointments.findById(replacement.getId())).isPresent();
    }

    @Test
    void adjacentBookingsAndDifferentEmployeesAreAllowedButPartialOverlapIsNot() {
        var f = fixture();
        appointments.saveAndFlush(booking(f, 0, 60, AppointmentStatus.PENDING));
        appointments.saveAndFlush(booking(f, 60, 150, AppointmentStatus.APPROVED));
        assertThatThrownBy(() -> appointments.saveAndFlush(booking(f, 30, 90, AppointmentStatus.APPROVED)))
                .isInstanceOf(DataIntegrityViolationException.class);
        var other = fixture();
        appointments.saveAndFlush(booking(other, 0, 60, AppointmentStatus.PENDING));
    }
}
