package com.appointmentplatform.appointment.application;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public record AvailableAppointmentSlotDetails (UUID serviceId, UUID employeeId, List<LocalTime> localTimes){
}
