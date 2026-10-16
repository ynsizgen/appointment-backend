package com.appointmentplatform;

import com.appointmentplatform.identity.application.PhoneVerificationService;
import com.appointmentplatform.identity.persistence.PhoneVerificationRepository;
import com.appointmentplatform.notification.SmsSender;
import com.appointmentplatform.shared.ApiException;
import com.appointmentplatform.shared.Crypto;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PhoneVerificationTests {
    @Autowired
    PhoneVerificationService service;

    @Autowired
    PhoneVerificationRepository repository;

    @Autowired
    Crypto crypto;

    @Autowired
    PlatformTransactionManager transactions;

    @MockitoBean
    SmsSender sender;

    @MockitoBean
    Clock clock;

    String phone;
    Instant now;
    final AtomicReference<String> sentCode = new AtomicReference<>();

    @BeforeEach
    void setup() {
        phone = "+90" + Long.toString(UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE).substring(0, 10);
        now = Instant.parse("2030-01-01T10:00:00Z");
        when(clock.instant()).thenAnswer(invocation -> now);
        doAnswer(invocation -> {
            sentCode.set(invocation.getArgument(1));
            return null;
        }).when(sender).sendVerificationCode(anyString(), anyString());
    }

    @Test
    void thirdWrongGuessLocksCodeAndAttemptsSurviveRollback() {
        service.request(phone);
        String code = sentCode.get();
        String wrong = code.equals("000000") ? "000001" : "000000";
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> service.verify(phone, wrong)).isInstanceOf(ApiException.class);
        }
        assertThat(repository.findById(crypto.keyedHash("phone-verification:" + phone)).orElseThrow().attempts()).isEqualTo(3);
        assertThatThrownBy(() -> service.verify(phone, code)).isInstanceOf(ApiException.class);
    }

    @Test
    void expiryIsExactlyTwoMinutes() {
        service.request(phone);
        String code = sentCode.get();
        now = now.plusSeconds(120);
        assertThatThrownBy(() -> service.verify(phone, code)).isInstanceOf(ApiException.class);
    }

    @Test
    void cooldownAndRollingHourlyLimitApplyAndResendInvalidatesOldCode() {
        service.request(phone);
        String oldCode = sentCode.get();
        String oldHash = repository.findById(crypto.keyedHash("phone-verification:" + phone)).orElseThrow().codeHash();
        assertThat(oldHash).isNotEqualTo(oldCode);
        now = now.plusSeconds(59);
        assertThatThrownBy(() -> service.request(phone)).isInstanceOf(ApiException.class);
        now = now.plusSeconds(1);
        service.request(phone);
        assertThatThrownBy(() -> service.verify(phone, oldCode)).isInstanceOf(ApiException.class);
        now = now.plusSeconds(60);
        service.request(phone);
        now = now.plusSeconds(60);
        assertThatThrownBy(() -> service.request(phone)).isInstanceOf(ApiException.class);
        now = Instant.parse("2030-01-01T11:00:00Z");
        service.request(phone);
    }

    @Test
    void proofIsBoundToPhoneSingleUseAndExpiresAfterFiveMinutes() {
        service.request(phone);
        var verified = service.verify(phone, sentCode.get());
        var tx = new TransactionTemplate(transactions);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> service.consume("+905550000000", verified.verificationToken())))
                .isInstanceOf(ApiException.class);
        tx.executeWithoutResult(status -> service.consume(phone, verified.verificationToken()));
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> service.consume(phone, verified.verificationToken())))
                .isInstanceOf(ApiException.class);
        now = now.plusSeconds(60);
        service.request(phone);
        var newProof = service.verify(phone, sentCode.get());
        now = now.plusSeconds(300);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> service.consume(phone, newProof.verificationToken())))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void failedAppointmentRollsBackConsumptionAndOnlyOneConcurrentConsumerSucceeds() throws Exception {
        service.request(phone);
        var verified = service.verify(phone, sentCode.get());
        var tx = new TransactionTemplate(transactions);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            service.consume(phone, verified.verificationToken());
            throw new IllegalStateException("booking failed");
        })).isInstanceOf(IllegalStateException.class);
        var barrier = new CyclicBarrier(2);
        Callable<Boolean> consume = () -> {
            barrier.await(10, TimeUnit.SECONDS);
            try {
                tx.executeWithoutResult(status -> service.consume(phone, verified.verificationToken()));
                return true;
            } catch (ApiException exception) {
                return false;
            }
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(consume);
            var second = executor.submit(consume);
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
    }

    @Test
    void proofFromPreviousVerificationIsInvalidatedByResend() {
        service.request(phone);
        var oldProof = service.verify(phone, sentCode.get());
        now = now.plusSeconds(60);
        service.request(phone);
        var tx = new TransactionTemplate(transactions);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> service.consume(phone, oldProof.verificationToken())))
                .isInstanceOf(ApiException.class);
        now = now.plusSeconds(119);
        assertThat(service.verify(phone, sentCode.get()).verificationToken()).isNotBlank();
    }

    @Test
    void concurrentRequestsSendOnlyOneCode() throws Exception {
        var barrier = new CyclicBarrier(2);
        Callable<Boolean> request = () -> {
            barrier.await(10, TimeUnit.SECONDS);
            try {
                service.request(phone);
                return true;
            } catch (ApiException exception) {
                assertThat(exception.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
                return false;
            }
        };
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(request);
            var second = executor.submit(request);
            assertThat(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
    }

    @Test
    void failedDeliveryDoesNotCreateUsableCode() {
        doThrow(new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "No SMS provider"))
                .when(sender).sendVerificationCode(anyString(), anyString());
        assertThatThrownBy(() -> service.request(phone)).isInstanceOf(ApiException.class);
        assertThat(repository.findById(crypto.keyedHash("phone-verification:" + phone))).isEmpty();
    }
}
