package com.appointmentplatform.notification.application;

import com.appointmentplatform.audit.Audit;
import com.appointmentplatform.notification.AppointmentReminderEligibility;
import com.appointmentplatform.notification.SmsJobType;
import com.appointmentplatform.notification.SmsSender;
import com.appointmentplatform.notification.domain.SmsJob;
import com.appointmentplatform.notification.domain.SmsJobStatus;
import com.appointmentplatform.notification.persistence.SmsJobRepository;
import com.appointmentplatform.shared.Crypto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.time.Clock;
import java.util.Optional;

@Service
@ConditionalOnProperty(name = "app.sms.worker-enabled", havingValue = "true", matchIfMissing = true)
public class AppointmentSmsWorker {

    private final SmsJobRepository smsJobRepository;
    private final SmsSender smsSender;
    private final Crypto crypto;
    private final Audit audit;
    private final AppointmentReminderEligibility appointmentReminderEligibility;

    private Clock clock;

    private static final Logger log = LoggerFactory.getLogger(AppointmentSmsWorker.class);

    public AppointmentSmsWorker(SmsJobRepository smsJobRepository, SmsSender smsSender, Crypto crypto, Audit audit, AppointmentReminderEligibility appointmentReminderEligibility, Clock clock) {
        this.smsJobRepository = smsJobRepository;
        this.smsSender = smsSender;
        this.crypto = crypto;
        this.audit = audit;
        this.appointmentReminderEligibility = appointmentReminderEligibility;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString ="${app.sms.poll-delay:7000}")
    @Transactional
    void delivery(){
        Optional<SmsJob> smsJob = smsJobRepository.findNextDue("PENDING", clock.instant());


        if (smsJob.isEmpty()) {
            return;
        }
        SmsJob job = smsJob.get();

        boolean reminder = (job.type() == SmsJobType.REMINDER_24_HOURS
                || job.type() == SmsJobType.REMINDER_2_HOURS);

        if(reminder && !appointmentReminderEligibility.canSendReminder(job.appointmentId())){
            job.markSkipped(clock.instant());
            return;
        }

        try{
            smsSender.sendMessage(job.recipient(),crypto.decrypt(job.encryptedBody()));
            job.markSent(clock.instant());

        }catch (Exception e){

            boolean retryAllowed = job.type() == SmsJobType.APPOINTMENT_CREATED
                            || job.type() == SmsJobType.REMINDER_24_HOURS
                            || job.type() == SmsJobType.REMINDER_2_HOURS;

            if (retryAllowed && job.attemptCount() == 0) {
                job.markAttempt(clock.instant());
            } else {
                job.markFailure(clock.instant());
            }

            log.warn(
                    "SMS gönderilemedi. jobId={}, appointmentId={}, attempt={}, status={}, nextAttemptAt={}, errorType={}",
                    job.id(),
                    job.appointmentId(),
                    job.attemptCount(),
                    job.status(),
                    job.status() == SmsJobStatus.PENDING ? job.nextAttemptAt() : null,
                    e.getClass().getSimpleName()
            );

            audit.failure(null, "SMS_DELIVERY_FAILED");
        }
    }
}
