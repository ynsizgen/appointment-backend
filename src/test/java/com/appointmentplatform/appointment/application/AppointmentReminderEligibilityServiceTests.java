package com.appointmentplatform.appointment.application;

import com.appointmentplatform.appointment.AppointmentStatus;
import com.appointmentplatform.appointment.domain.Appointment;
import com.appointmentplatform.appointment.persistence.AppointmentRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AppointmentReminderEligibilityServiceTests {
    private final Instant now = Instant.parse("2030-01-01T10:00:00Z");
    private final AppointmentRepository repository = mock(AppointmentRepository.class);
    private final AppointmentReminderEligibilityService service = new AppointmentReminderEligibilityService(
            repository, Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void missingAppointmentIsNotEligible() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        assertThat(service.canSendReminder(id)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({"PENDING,3600,true", "APPROVED,3600,true", "PENDING,0,false",
            "APPROVED,0,false", "PENDING,-1,false", "APPROVED,-1,false",
            "REJECTED,3600,false", "CANCELLED,3600,false"})
    void checksStatusAndStrictStartBoundary(AppointmentStatus status, long seconds, boolean expected) {
        UUID id = UUID.randomUUID();
        Appointment appointment = mock(Appointment.class);
        when(repository.findById(id)).thenReturn(Optional.of(appointment));
        when(appointment.getStatus()).thenReturn(status);
        when(appointment.getStartsAt()).thenReturn(now.plusSeconds(seconds));
        assertThat(service.canSendReminder(id)).isEqualTo(expected);
    }
}
