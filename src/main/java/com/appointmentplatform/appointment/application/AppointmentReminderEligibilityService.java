package com.appointmentplatform.appointment.application;

import com.appointmentplatform.appointment.AppointmentStatus;
import com.appointmentplatform.appointment.domain.Appointment;
import com.appointmentplatform.appointment.persistence.AppointmentRepository;
import com.appointmentplatform.notification.AppointmentReminderEligibility;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class AppointmentReminderEligibilityService implements AppointmentReminderEligibility {

    private final AppointmentRepository appointmentRepository;
    private final Clock clock;


    public AppointmentReminderEligibilityService(AppointmentRepository appointmentRepository, Clock clock) {
        this.appointmentRepository = appointmentRepository;
        this.clock = clock;
    }

    @Override
    public boolean canSendReminder(UUID appointmentId) {
        Appointment appointment = appointmentRepository.findById(appointmentId).orElse(null);
        if (appointment == null) {
            return false;
        }

        boolean active = appointment.getStatus() == AppointmentStatus.PENDING
                || appointment.getStatus() == AppointmentStatus.APPROVED;

        return active && appointment.getStartsAt().isAfter(clock.instant());
    }
}
