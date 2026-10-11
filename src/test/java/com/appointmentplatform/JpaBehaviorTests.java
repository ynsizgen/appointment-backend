package com.appointmentplatform;

import com.appointmentplatform.identity.application.*;
import com.appointmentplatform.identity.domain.*;
import com.appointmentplatform.identity.persistence.*;
import com.appointmentplatform.identity.security.*;
import com.appointmentplatform.notification.application.VerificationMailWorker;
import com.appointmentplatform.notification.infrastructure.VerificationMailSender;
import com.appointmentplatform.notification.persistence.EmailJobRepository;
import com.appointmentplatform.audit.persistence.AuditEventRepository;
import com.appointmentplatform.shared.*;
import com.appointmentplatform.brand.application.BrandService;
import com.appointmentplatform.brand.persistence.BrandRepository;
import com.appointmentplatform.service.web.dto.ServiceRequest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class JpaBehaviorTests {
    @Autowired IdentityService identity;
    @Autowired PendingRegistrationRepository registrations;
    @Autowired UserAccountRepository users;
    @Autowired BrandRepository brands;
    @Autowired BrandService brandService;
    @Autowired com.appointmentplatform.branch.persistence.BranchRepository branches;
    @Autowired com.appointmentplatform.branch.application.BranchService branchService;
    @Autowired EmailJobRepository emails;
    @Autowired AuditEventRepository audit;
    @Autowired RateLimits limits;
    @Autowired Crypto crypto;
    @Autowired Clock clock;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void concurrentRegistrationCreatesOnePendingRecordAndOneEmail() throws Exception {
        var command = registration();
        assertThat(race(() -> { identity.register(command); return "accepted"; }))
                .containsExactly("accepted", "accepted");
        var pending = registrations.findByEmail(command.email()).orElseThrow();
        assertThat(emails.findByRegistrationId(pending.id())).isPresent();
        assertThat(users.findByEmail(command.email())).isEmpty();
    }

    @Test
    void concurrentVerificationCreatesExactlyOneAccountAndBrandWithoutBranch() throws Exception {
        var command = registration();
        identity.register(command);
        String token = token(command.email());
        var outcomes = race(() -> {
            try { identity.verify(token); return "verified"; }
            catch (ApiException failure) {
                assertThat(failure.status()).isEqualTo(HttpStatus.BAD_REQUEST);
                return "consumed";
            }
        });
        assertThat(outcomes).containsExactlyInAnyOrder("verified", "consumed");
        var user = users.findByEmail(command.email()).orElseThrow();
        assertThat(user.role()).isEqualTo(Role.BRAND);
        assertThat(brands.findById(user.brandId()).orElseThrow().displayName()).isEqualTo(command.displayName());
        assertThat(user.enabled()).isTrue();
        assertThat(user.isSubscriptionActive()).isFalse();
        var initial = branches.findByBrandIdOrderByCreatedAtAscIdAsc(user.brandId());
        assertThat(initial).isEmpty();
        assertThat(registrations.findByEmail(command.email())).isEmpty();
    }

    @Test
    void registrationAndNotificationRollBackTogether() {
        var command = registration();
        long emailCount = emails.count();
        long auditCount = audit.count();
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            identity.register(command);
            throw new IllegalStateException("Simulated transaction failure");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(registrations.findByEmail(command.email())).isEmpty();
        assertThat(emails.count()).isEqualTo(emailCount);
        assertThat(audit.count()).isEqualTo(auditCount);
    }

    @Test
    void expiredVerificationDoesNotCreateAnAccount() {
        var command = registration();
        String raw = crypto.token();
        registrations.saveAndFlush(new PendingRegistration(UUID.randomUUID(), command.email(), "unused",
                "Test", "User", command.phone(), "Brand",
                Crypto.hash(raw), clock.instant().minusSeconds(1)));
        assertThatThrownBy(() -> identity.verify(raw)).isInstanceOf(ApiException.class);
        assertThat(users.findByEmail(command.email())).isEmpty();
    }

    @Test
    void concurrentRateLimitChecksCannotBothPassALimitOfOne() throws Exception {
        String subject = UUID.randomUUID().toString();
        var outcomes = race(() -> {
            try { limits.check("concurrent-test", subject, 1, 3600); return "allowed"; }
            catch (ApiException failure) {
                assertThat(failure.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                return "limited";
            }
        });
        assertThat(outcomes).containsExactlyInAnyOrder("allowed", "limited");
        assertThatThrownBy(() -> limits.check("concurrent-test", subject, 1, 3600)).isInstanceOf(ApiException.class);
    }

    @Test
    void mailWorkerUsesOnlyNotificationDataAndDoesNotSendTwice() {
        var command = registration();
        identity.register(command);
        UUID jobId = emails.findByRegistrationId(registrations.findByEmail(command.email()).orElseThrow().id()).orElseThrow().id();
        var sender = mock(VerificationMailSender.class);
        // Isolate this job from other tests' queued messages.
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            emails.findAll().stream().filter(job -> !job.id().equals(jobId)).forEach(job -> job.finish("FAILED", clock.instant()));
        });
        var worker = new VerificationMailWorker(emails, sender, clock);
        var tx = new TransactionTemplate(transactions);
        tx.executeWithoutResult(status -> worker.deliver());
        tx.executeWithoutResult(status -> worker.deliver());
        verify(sender, times(1)).send(eq(command.email()), anyString());
        var job = emails.findById(jobId).orElseThrow();
        assertThat(job.state()).isEqualTo("SENT");
        assertThat(job.encryptedBody()).isNull();
    }

    @Test
    void verificationRollsBackAccountBrandAndBranchTogether() {
        var command = registration();
        identity.register(command);
        String raw = token(command.email());
        long brandCount = brands.count();
        long branchCount = branches.count();
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            identity.verify(raw);
            throw new IllegalStateException("Simulated failure after verification");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(users.findByEmail(command.email())).isEmpty();
        assertThat(brands.count()).isEqualTo(brandCount);
        assertThat(branches.count()).isEqualTo(branchCount);
        assertThat(registrations.findByEmail(command.email())).isPresent();
        identity.verify(raw);
        assertThat(users.findByEmail(command.email())).isPresent();
    }

    private RegistrationCommand registration() {
        return new RegistrationCommand("jpa-" + UUID.randomUUID() + "@example.com", "a-secure-password-123",
                "Ada", "Lovelace", "+905551112233", "JPA Brand");
    }

    private String token(String email) {
        var pending = registrations.findByEmail(email).orElseThrow();
        String body = crypto.decrypt(emails.findByRegistrationId(pending.id()).orElseThrow().encryptedBody());
        return body.substring(body.indexOf("#token=") + 7).strip();
    }

    private <T> List<T> race(Callable<T> work) throws Exception {
        try (var executor = Executors.newFixedThreadPool(2)) {
            var ready = new CountDownLatch(2);
            var start = new CountDownLatch(1);
            Callable<T> synchronizedWork = () -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
                return work.call();
            };
            var first = executor.submit(synchronizedWork);
            var second = executor.submit(synchronizedWork);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            return List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
        }
    }
}
