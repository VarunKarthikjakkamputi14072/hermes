package com.hermes.worker.service;

import com.hermes.common.domain.Account;
import com.hermes.common.domain.Payment;
import com.hermes.common.domain.PaymentStatus;
import com.hermes.common.event.PaymentRequestedEvent;
import com.hermes.common.repository.AccountRepository;
import com.hermes.common.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@EntityScan("com.hermes.common.domain")
@EnableJpaRepositories("com.hermes.common.repository")
@Import(LedgerService.class)
class LedgerServiceTest {

    @Autowired
    PaymentRepository paymentRepository;
    @Autowired
    AccountRepository accountRepository;
    @Autowired
    LedgerService service;

    @BeforeEach
    void seed() {
        accountRepository.save(new Account("ACC-1", "Alice", 10_000)); // $100.00
    }

    private PaymentRequestedEvent persistPayment(String accountId, long amountCents) {
        Payment payment = new Payment(UUID.randomUUID(), "idem-" + UUID.randomUUID(), accountId, amountCents);
        paymentRepository.save(payment);
        return new PaymentRequestedEvent(
                payment.getId(), payment.getIdempotencyKey(), accountId, amountCents, Instant.now());
    }

    @Test
    void appliesWhenFunded() {
        PaymentRequestedEvent event = persistPayment("ACC-1", 2_500); // $25

        LedgerResult result = service.apply(event);

        assertThat(result).isEqualTo(LedgerResult.APPLIED);
        assertThat(paymentRepository.findById(event.paymentId()).orElseThrow().getStatus())
                .isEqualTo(PaymentStatus.APPLIED);
        assertThat(accountRepository.findById("ACC-1").orElseThrow().getBalanceCents())
                .isEqualTo(7_500); // 100 - 25
    }

    @Test
    void rejectsWhenInsufficientFunds() {
        PaymentRequestedEvent event = persistPayment("ACC-1", 999_999);

        LedgerResult result = service.apply(event);

        assertThat(result).isEqualTo(LedgerResult.REJECTED_INSUFFICIENT_FUNDS);
        assertThat(paymentRepository.findById(event.paymentId()).orElseThrow().getFailureReason())
                .isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(accountRepository.findById("ACC-1").orElseThrow().getBalanceCents())
                .isEqualTo(10_000); // unchanged
    }

    @Test
    void rejectsUnknownAccount() {
        PaymentRequestedEvent event = persistPayment("ACC-NOPE", 100);

        assertThat(service.apply(event)).isEqualTo(LedgerResult.REJECTED_UNKNOWN_ACCOUNT);
    }

    @Test
    void isIdempotentOnRedelivery() {
        PaymentRequestedEvent event = persistPayment("ACC-1", 3_000); // $30

        assertThat(service.apply(event)).isEqualTo(LedgerResult.APPLIED);
        // redelivery of the same payment must not debit twice
        assertThat(service.apply(event)).isEqualTo(LedgerResult.SKIPPED_DUPLICATE);
        assertThat(accountRepository.findById("ACC-1").orElseThrow().getBalanceCents())
                .isEqualTo(7_000); // debited exactly once
    }
}
