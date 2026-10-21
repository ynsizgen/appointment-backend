package com.appointmentplatform;

import com.appointmentplatform.notification.AppointmentSmsJob;
import com.appointmentplatform.notification.AppointmentSmsJobs;
import com.appointmentplatform.notification.SmsJobType;
import com.appointmentplatform.notification.domain.SmsJob;
import com.appointmentplatform.notification.domain.SmsJobStatus;
import com.appointmentplatform.notification.persistence.SmsJobRepository;
import com.appointmentplatform.shared.Crypto;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SmsJobPersistenceTests {
    @Autowired
    SmsJobRepository repository;
    @Autowired
    AppointmentSmsJobs jobs;
    @Autowired
    Crypto crypto;
    @Autowired
    PlatformTransactionManager transactions;

    private SmsJob job(Instant due) {
        return new SmsJob(UUID.randomUUID(), UUID.randomUUID(), "+905551112233",
                crypto.encrypt("Test message"), SmsJobType.APPOINTMENT_CREATED, due);
    }

    @Test
    @Transactional
    void selectsOnlyOneDuePendingJobInOrderAndHonorsRetryTime() {
        repository.deleteAllInBatch();
        Instant now = Instant.parse("2030-01-01T10:00:00Z");
        var failed = job(now.minusSeconds(300));
        failed.markFailure(now);
        repository.saveAndFlush(failed);
        var sent = job(now.minusSeconds(200));
        sent.markSent(now);
        repository.saveAndFlush(sent);
        var first = repository.saveAndFlush(job(now.minusSeconds(100)));
        var boundary = repository.saveAndFlush(job(now));
        var future = repository.saveAndFlush(job(now.plusSeconds(601)));

        assertThat(repository.findNextDue("PENDING", now)).get().extracting(SmsJob::id).isEqualTo(first.id());
        first.markAttempt(now);
        repository.flush();
        assertThat(repository.findNextDue("PENDING", now)).get().extracting(SmsJob::id).isEqualTo(boundary.id());
        boundary.markSent(now);
        repository.flush();
        assertThat(repository.findNextDue("PENDING", now.plusSeconds(599))).isEmpty();
        assertThat(repository.findNextDue("PENDING", now.plusSeconds(600))).get().extracting(SmsJob::id).isEqualTo(first.id());
        assertThat(future.status()).isEqualTo(SmsJobStatus.PENDING);
    }

    @Test
    void enqueueRequiresTransactionEncryptsBodyAndRollsBackWithCaller() {
        UUID appointmentId = UUID.randomUUID();
        var request = new AppointmentSmsJob(appointmentId, "+905551112233",
                "https://example.invalid/randevu#token=test-secret", SmsJobType.APPOINTMENT_CREATED, Instant.now());
        assertThatThrownBy(() -> jobs.enqueue(request)).isInstanceOf(IllegalTransactionStateException.class);
        var tx = new TransactionTemplate(transactions);
        UUID id = tx.execute(status -> {
            jobs.enqueue(request);
            var saved = repository.findAll().stream()
                    .filter(job -> job.appointmentId().equals(appointmentId)).findFirst().orElseThrow();
            assertThat(saved.encryptedBody()).doesNotContain("test-secret");
            assertThat(crypto.decrypt(saved.encryptedBody())).isEqualTo(request.body());
            assertThat(saved.status()).isEqualTo(SmsJobStatus.PENDING);
            status.setRollbackOnly();
            return saved.id();
        });
        assertThat(repository.findById(id)).isEmpty();
    }

    @Test
    void concurrentWorkerSkipsLockedJobAndRollbackReleasesIt() throws Exception {
        Instant now = Instant.parse("1900-01-01T10:00:00Z");
        var first = repository.saveAndFlush(job(now.minusSeconds(1)));
        var second = repository.saveAndFlush(job(now));
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var tx = new TransactionTemplate(transactions);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var owner = executor.submit(() -> tx.execute(status -> {
                var selected = repository.findNextDue("PENDING", now).orElseThrow();
                assertThat(selected.id()).isEqualTo(first.id());
                locked.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting for second worker");
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                status.setRollbackOnly();
                return selected.id();
            }));
            try {
                assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
                var other = executor.submit(() -> tx.execute(status -> {
                    var selected = repository.findNextDue("PENDING", now).orElseThrow();
                    selected.markSent(now);
                    return selected.id();
                }));
                // Must complete while the first transaction still owns its lock.
                assertThat(other.get(5, TimeUnit.SECONDS)).isEqualTo(second.id());
            } finally {
                release.countDown();
            }
            assertThat(owner.get(5, TimeUnit.SECONDS)).isEqualTo(first.id());
            UUID releasedId = tx.execute(status -> repository.findNextDue("PENDING", now).orElseThrow().id());
            assertThat(releasedId).isEqualTo(first.id());
            assertThat(repository.findById(second.id()).orElseThrow().status()).isEqualTo(SmsJobStatus.SENT);
        } finally {
            repository.deleteById(first.id());
            repository.deleteById(second.id());
        }
    }
}
