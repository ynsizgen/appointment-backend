package com.appointmentplatform;

import com.appointmentplatform.appointment.application.ApplicationService;
import com.appointmentplatform.appointment.domain.Appointment;
import com.appointmentplatform.appointment.persistence.AppointmentRepository;
import com.appointmentplatform.branch.BranchOperations;
import com.appointmentplatform.employee.*;
import com.appointmentplatform.service.*;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AvailableStartTimesTests {
    static Stream<Arguments> scenarios() {
        return Stream.of(
            Arguments.of("No bookings; exact closing boundary", "09:00-12:00", "", 60, "09:00,10:00,11:00"),
            Arguments.of("Two bookings", "09:00-18:00", "0/10:00-0/11:30,0/14:00-0/15:00", 60, "09:00,11:30,12:30,15:00,16:00,17:00"),
            Arguments.of("Outside working hours", "09:00-12:00", "0/14:00-0/15:00", 60, "09:00,10:00,11:00"),
            Arguments.of("Entire shift occupied", "09:00-12:00", "0/08:00-0/13:00", 60, ""),
            Arguments.of("Touching boundaries", "09:00-12:00", "0/08:00-0/09:00,0/12:00-0/13:00", 60, "09:00,10:00,11:00"),
            Arguments.of("Nested and overlapping bookings", "09:00-15:00", "0/10:00-0/12:00,0/10:30-0/11:00,0/11:30-0/13:00", 60, "09:00,13:00,14:00"),
            Arguments.of("Split shift", "09:00-12:00,15:00-18:00", "0/11:00-0/16:00", 60, "09:00,10:00,16:00,17:00"),
            Arguments.of("Booking starts previous day", "09:00-12:00", "-1/23:00-0/10:30", 30, "10:30,11:00,11:30"),
            Arguments.of("Booking ends next day", "09:00-12:00", "0/10:00-1/01:00", 60, "09:00"),
            Arguments.of("Ninety minute service restarts after booking", "09:00-15:00", "0/10:00-0/11:30", 90, "11:30,13:00"),
            Arguments.of("Thirty minute service", "09:00-12:00", "0/10:00-0/11:30", 30, "09:00,09:30,11:30"),
            Arguments.of("One hour threshold", "07:00-10:00", "", 30, "08:00,08:30,09:00,09:30"),
            Arguments.of("Closed day", "", "", 60, "")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void computesAvailableStarts(String name, String hours, String bookings, int duration, String expected) {
        var date = LocalDate.of(2026, 10, 16);
        var zone = ZoneId.of("Europe/Istanbul");
        UUID brand = UUID.randomUUID(), employee = UUID.randomUUID(), service = UUID.randomUUID();
        var schedules = mock(EmployeeScheduleOperations.class);
        var employees = mock(EmployeeOperations.class);
        var services = mock(ServiceOperations.class);
        var repository = mock(AppointmentRepository.class);
        var intervals = new ArrayList<EmployeeTimeIntervalDetails>();
        if (!hours.isEmpty()) for (String pair : hours.split(",")) {
            var times = pair.split("-");
            intervals.add(new EmployeeTimeIntervalDetails(LocalTime.parse(times[0]), LocalTime.parse(times[1])));
        }
        var appointments = new ArrayList<Appointment>();
        if (!bookings.isEmpty()) for (String pair : bookings.split(",")) {
            // Split on the delimiter preceding the second signed day offset.
            int separator = pair.indexOf('-', pair.indexOf('/'));
            var appointment = mock(Appointment.class);
            when(appointment.getStartsAt()).thenReturn(instant(date, zone, pair.substring(0, separator)));
            when(appointment.getEndsAt()).thenReturn(instant(date, zone, pair.substring(separator + 1)));
            appointments.add(appointment);
        }
        when(schedules.daily(brand, employee, date)).thenReturn(new EmployeeDailyScheduleDetails(date, "BRANCH", true, intervals, intervals));
        when(services.findById(service)).thenReturn(new ServiceDetails(service, brand, "Service", duration));
        when(repository.findBlockingInRange(employee, date.atStartOfDay(zone).toInstant(), date.plusDays(1).atStartOfDay(zone).toInstant())).thenReturn(appointments);
        var application = new ApplicationService(schedules, employees, services,
                Clock.fixed(date.atTime(7, 0).atZone(zone).toInstant(), ZoneOffset.UTC), repository, mock(BranchOperations.class));
        List<LocalTime> wanted = expected.isEmpty() ? List.of() : Arrays.stream(expected.split(",")).map(LocalTime::parse).toList();
        assertThat(application.getAvailableStartTimes(brand, service, employee, date).localTimes()).containsExactlyElementsOf(wanted);
        verify(services).requireOwnedBy(service, brand);
        verify(employees).requireServiceAssigned(brand, employee, service);
    }

    private static Instant instant(LocalDate date, ZoneId zone, String value) {
        var parts = value.split("/");
        return date.plusDays(Integer.parseInt(parts[0])).atTime(LocalTime.parse(parts[1])).atZone(zone).toInstant();
    }
}
