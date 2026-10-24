package com.appointmentplatform.notification.application;

import com.appointmentplatform.notification.AppointmentSmsJob;
import com.appointmentplatform.notification.AppointmentSmsJobs;
import com.appointmentplatform.notification.SmsJobType;
import com.appointmentplatform.notification.domain.SmsJob;
import com.appointmentplatform.notification.domain.SmsJobStatus;
import com.appointmentplatform.notification.persistence.SmsJobRepository;
import com.appointmentplatform.shared.Crypto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class NotificationSmsService implements AppointmentSmsJobs {
    private final SmsJobRepository smsJobRepository;
    private final Crypto crypto;

    public NotificationSmsService(SmsJobRepository smsJobRepository,Crypto crypto) {
        this.smsJobRepository = smsJobRepository;
        this.crypto = crypto;
    }


    @Transactional(propagation = Propagation.MANDATORY)
    public void enqueue(AppointmentSmsJob appointmentSmsJob) {
        smsJobRepository.saveAndFlush(new SmsJob(UUID.randomUUID(),
                                    appointmentSmsJob.appointmentId(),
                                    appointmentSmsJob.recipient(),
                                    crypto.encrypt(appointmentSmsJob.body()),
                                    appointmentSmsJob.type(),
                                    appointmentSmsJob.nextAttemptAt()));
    }
}
