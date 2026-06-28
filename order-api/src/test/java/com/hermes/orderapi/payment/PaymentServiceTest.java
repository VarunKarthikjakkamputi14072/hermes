package com.hermes.orderapi.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.common.domain.OutboxEvent;
import com.hermes.common.domain.Payment;
import com.hermes.common.repository.OutboxRepository;
import com.hermes.common.repository.PaymentRepository;
import com.hermes.orderapi.web.dto.CreatePaymentRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@EntityScan("com.hermes.common.domain")
@EnableJpaRepositories("com.hermes.common.repository")
class PaymentServiceTest {

    @Autowired
    PaymentRepository paymentRepository;
    @Autowired
    OutboxRepository outboxRepository;

    private PaymentService service() {
        return new PaymentService(paymentRepository, outboxRepository,
                new ObjectMapper().findAndRegisterModules());
    }

    @Test
    void writesPaymentAndOutboxEventAtomically() {
        Payment payment = service().acceptCharge(new CreatePaymentRequest("ACC-1", 2_500, "key-1"));

        // the payment row exists...
        assertThat(paymentRepository.findById(payment.getId())).isPresent();
        // ...and exactly one outbox event was written in the same unit of work
        assertThat(outboxRepository.count()).isEqualTo(1);

        OutboxEvent event = outboxRepository.findAll().get(0);
        assertThat(event.getAggregateType()).isEqualTo("payments.requested"); // → Kafka topic
        assertThat(event.getAggregateId()).isEqualTo("ACC-1");                 // → Kafka key
        assertThat(event.getType()).isEqualTo("PaymentRequested");
        assertThat(event.getPayload())
                .contains(payment.getId().toString())
                .contains("\"amountCents\":2500")
                .contains("\"accountId\":\"ACC-1\"");
    }
}
