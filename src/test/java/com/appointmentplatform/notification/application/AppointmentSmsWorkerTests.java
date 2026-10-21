package com.appointmentplatform.notification.application;

import com.appointmentplatform.audit.Audit;
import com.appointmentplatform.notification.SmsJobType;
import com.appointmentplatform.notification.SmsSender;
import com.appointmentplatform.notification.domain.SmsJob;
import com.appointmentplatform.notification.domain.SmsJobStatus;
import com.appointmentplatform.notification.persistence.SmsJobRepository;
import com.appointmentplatform.shared.Crypto;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AppointmentSmsWorkerTests {
    private final Instant now = Instant.parse("2030-01-01T07:00:00Z");
    private final SmsJobRepository repository = mock(SmsJobRepository.class);
    private final SmsSender sender = mock(SmsSender.class);
    private final Audit audit = mock(Audit.class);
    private final Crypto crypto = new Crypto("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");

    private AppointmentSmsWorker worker(Instant time, SmsJob job) {
        when(repository.findNextDue(SmsJobStatus.PENDING.name(), time))
                .thenReturn(Optional.ofNullable(job));
        return new AppointmentSmsWorker(repository, sender, crypto, audit, Clock.fixed(time, ZoneOffset.UTC));
    }

    private SmsJob job(SmsJobType type) {
        return new SmsJob(UUID.randomUUID(), UUID.randomUUID(), "+905551112233",
                crypto.encrypt("Test appointment message"), type, now);
    }

    @Test
    void emptyQueueDoesNotSendOrAudit() {
        worker(now, null).delivery();
        verifyNoInteractions(sender, audit);
    }

    @Test
    void successfulDeliveryDecryptsMessageAndClearsStoredBody() {
        var job = job(SmsJobType.APPOINTMENT_CREATED);
        worker(now, job).delivery();
        verify(sender).sendMessage(job.recipient(), "Test appointment message");
        assertThat(job.status()).isEqualTo(SmsJobStatus.SENT);
        assertThat(job.attemptCount()).isEqualTo(1);
        assertThat(job.completedAt()).isEqualTo(now);
        assertThat(job.encryptedBody()).isNull();
        verifyNoInteractions(audit);
    }

    @ParameterizedTest
    @EnumSource(value = SmsJobType.class, names = {"APPOINTMENT_CREATED", "REMINDER_24_HOURS", "REMINDER_2_HOURS"})
    void retryableMessagesWaitTenMinutesAndStopAfterSecondFailure(SmsJobType type) {
        var job = job(type);
        doThrow(new IllegalStateException("Provider unavailable")).when(sender).sendMessage(anyString(), anyString());
        worker(now, job).delivery();
        assertThat(job.status()).isEqualTo(SmsJobStatus.PENDING);
        assertThat(job.attemptCount()).isEqualTo(1);
        assertThat(job.nextAttemptAt()).isEqualTo(now.plus(Duration.ofMinutes(10)));
        assertThat(job.completedAt()).isNull();
        assertThat(job.encryptedBody()).isNotNull();

        Instant retryAt = job.nextAttemptAt();
        worker(retryAt, job).delivery();
        assertThat(job.status()).isEqualTo(SmsJobStatus.FAILED);
        assertThat(job.attemptCount()).isEqualTo(2);
        assertThat(job.completedAt()).isEqualTo(retryAt);
        assertThat(job.encryptedBody()).isNull();
        verify(audit, times(2)).failure(null, "SMS_DELIVERY_FAILED");
    }

    @ParameterizedTest
    @EnumSource(value = SmsJobType.class, names = {"APPOINTMENT_REJECTED", "APPOINTMENT_CANCELLED"})
    void rejectionAndCancellationDoNotRetry(SmsJobType type) {
        var job = job(type);
        doThrow(new IllegalStateException("Provider unavailable")).when(sender).sendMessage(anyString(), anyString());
        worker(now, job).delivery();
        assertThat(job.status()).isEqualTo(SmsJobStatus.FAILED);
        assertThat(job.attemptCount()).isEqualTo(1);
        assertThat(job.completedAt()).isEqualTo(now);
        assertThat(job.encryptedBody()).isNull();
    }

    @Test
    void retryCanSucceedOnSecondAttempt() {
        var job = job(SmsJobType.APPOINTMENT_CREATED);
        doThrow(new IllegalStateException("Provider unavailable")).doNothing()
                .when(sender).sendMessage(anyString(), anyString());
        worker(now, job).delivery();
        Instant retryAt = job.nextAttemptAt();
        worker(retryAt, job).delivery();
        assertThat(job.status()).isEqualTo(SmsJobStatus.SENT);
        assertThat(job.attemptCount()).isEqualTo(2);
        assertThat(job.completedAt()).isEqualTo(retryAt);
    }
}
