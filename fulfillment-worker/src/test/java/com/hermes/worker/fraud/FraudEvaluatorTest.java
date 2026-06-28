package com.hermes.worker.fraud;

import com.hermes.common.domain.Payment;
import com.hermes.common.domain.PaymentStatus;
import com.hermes.common.domain.RiskDecision;
import com.hermes.common.repository.FraudFlagRepository;
import com.hermes.common.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@EntityScan("com.hermes.common.domain")
@EnableJpaRepositories("com.hermes.common.repository")
@Import(FraudEvaluator.class)
class FraudEvaluatorTest {

    @Autowired
    PaymentRepository paymentRepository;
    @Autowired
    FraudFlagRepository fraudFlagRepository;
    @Autowired
    FraudEvaluator evaluator;

    private void persist(String accountId, long amountCents, PaymentStatus status) {
        Payment p = new Payment(UUID.randomUUID(), "k-" + UUID.randomUUID(), accountId, amountCents);
        if (status == PaymentStatus.APPLIED) p.markApplied();
        else if (status == PaymentStatus.REJECTED) p.markRejected("INSUFFICIENT_FUNDS");
        paymentRepository.save(p);
    }

    @Test
    void doesNotFlagOrdinaryActivity() {
        persist("ACC-1", 1_000, PaymentStatus.APPLIED);

        evaluator.evaluate(UUID.randomUUID(), "ACC-1", 1_000);

        assertThat(fraudFlagRepository.count()).isZero();
    }

    @Test
    void flagsCardTestingPattern() {
        // a run of insufficient-funds rejections — classic card-testing
        for (int i = 0; i < 4; i++) {
            persist("ACC-1", 500, PaymentStatus.REJECTED);
        }

        evaluator.evaluate(UUID.randomUUID(), "ACC-1", 500);

        assertThat(fraudFlagRepository.count()).isEqualTo(1);
        var flag = fraudFlagRepository.findAll().get(0);
        assertThat(flag.getDecision()).isIn(RiskDecision.REVIEW, RiskDecision.HOLD);
        assertThat(flag.getReasons()).contains("card-testing");
        assertThat(flag.isNarrativePending()).isTrue();
    }

    @Test
    void holdsWhenVelocityAndCardTestingCombine() {
        for (int i = 0; i < 6; i++) {
            persist("ACC-2", 500, i < 3 ? PaymentStatus.REJECTED : PaymentStatus.APPLIED);
        }

        evaluator.evaluate(UUID.randomUUID(), "ACC-2", 500);

        var flag = fraudFlagRepository.findAll().get(0);
        assertThat(flag.getDecision()).isEqualTo(RiskDecision.HOLD);
        assertThat(flag.getRiskScore()).isGreaterThanOrEqualTo(70);
    }
}
