package com.appointmentplatform;

import com.appointmentplatform.appointment.application.ApplicationService;
import com.appointmentplatform.appointment.persistence.AppointmentRepository;
import com.appointmentplatform.appointment.web.dto.AppointmentCreateRequest;
import com.appointmentplatform.branch.BranchOperations;
import com.appointmentplatform.identity.SubscriptionOperations;
import com.appointmentplatform.identity.PhoneVerifications;
import com.appointmentplatform.employee.*;
import com.appointmentplatform.service.*;
import com.appointmentplatform.shared.ApiException;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AppointmentCreationTests {
    final UUID brand = UUID.randomUUID(), branch = UUID.randomUUID(), employee = UUID.randomUUID(), service = UUID.randomUUID();
    final EmployeeScheduleOperations schedules = mock(EmployeeScheduleOperations.class);
    final EmployeeOperations employees = mock(EmployeeOperations.class);
    final ServiceOperations services = mock(ServiceOperations.class);
    final BranchOperations branches = mock(BranchOperations.class);
    final AppointmentRepository repository = mock(AppointmentRepository.class);
    final LocalDate date = LocalDate.of(2026, 10, 12);
    ApplicationService application;

    @BeforeEach
    void setup() {
        // 10:00 in Istanbul: customer boundary is precisely 11:00.
        application = new ApplicationService(schedules, employees, services,
                Clock.fixed(Instant.parse("2026-10-12T07:00:00Z"), ZoneOffset.UTC), repository, branches, mock(SubscriptionOperations.class), mock(PhoneVerifications.class));
        when(branches.brandId(branch)).thenReturn(brand);
        when(employees.branchId(employee)).thenReturn(branch);
        when(services.findById(service)).thenReturn(new ServiceDetails(service, brand, "Cut", 60));
        var intervals = List.of(new EmployeeTimeIntervalDetails(LocalTime.of(8, 0), LocalTime.of(20, 0)));
        when(schedules.daily(brand, employee, date)).thenReturn(new EmployeeDailyScheduleDetails(date, "BRANCH", true, intervals, intervals));
        when(repository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));
    }

    @Test void customerCanBookExactlyOneHourAhead() {
        assertThat(application.createByCustomer(branch, request(LocalTime.of(11, 0)), "proof").startsAt())
                .isEqualTo(Instant.parse("2026-10-12T08:00:00Z"));
    }
    @Test void customerCannotBookOneSecondBeforeBoundary() {
        assertThatThrownBy(() -> application.createByCustomer(branch, request(LocalTime.of(10, 59, 59)), "proof"))
                .isInstanceOf(ApiException.class).hasMessageContaining("1 saat");
        verify(repository, never()).saveAndFlush(any());
    }
    @Test void brandCanBookEarlierThanCurrentTime() {
        assertThat(application.createByBrand(brand, request(LocalTime.of(9, 0))).startsAt())
                .isEqualTo(Instant.parse("2026-10-12T06:00:00Z"));
    }
    @Test void appointmentCannotWrapIntoNextDay() {
        assertThatThrownBy(() -> application.createByCustomer(branch, request(LocalTime.of(23, 30)), "proof"))
                .isInstanceOf(ApiException.class).hasMessageContaining("çalışma saatlerinin");
        verify(repository, never()).saveAndFlush(any());
    }
    private AppointmentCreateRequest request(LocalTime start) {
        return new AppointmentCreateRequest(employee, service, date, start, "Ada", "Lovelace", "+905551112233", null);
    }
}
