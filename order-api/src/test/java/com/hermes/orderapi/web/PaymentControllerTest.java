package com.hermes.orderapi.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.common.domain.Payment;
import com.hermes.common.event.PaymentRequestedEvent;
import com.hermes.common.repository.PaymentRepository;
import com.hermes.orderapi.kafka.PaymentProducer;
import com.hermes.orderapi.metrics.PaymentMetrics;
import com.hermes.orderapi.web.dto.CreatePaymentRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentController.class)
class PaymentControllerTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    PaymentRepository paymentRepository;
    @MockBean
    PaymentProducer paymentProducer;
    @MockBean
    PaymentMetrics paymentMetrics;

    @Test
    void acceptsNewChargeAndPublishesEvent() throws Exception {
        when(paymentRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.empty());
        when(paymentRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        CreatePaymentRequest body = new CreatePaymentRequest("ACC-1", 2_500, "key-1");

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.deduplicated").value(false))
                .andExpect(jsonPath("$.amountCents").value(2500));

        verify(paymentProducer).publish(any(PaymentRequestedEvent.class));
    }

    @Test
    void deduplicatesOnRepeatKeyWithoutChargingAgain() throws Exception {
        Payment existing = new Payment(UUID.randomUUID(), "key-1", "ACC-1", 2_500);
        existing.markApplied();
        when(paymentRepository.findByIdempotencyKey("key-1")).thenReturn(Optional.of(existing));

        CreatePaymentRequest body = new CreatePaymentRequest("ACC-1", 2_500, "key-1");

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deduplicated").value(true))
                .andExpect(jsonPath("$.status").value("APPLIED"));

        verify(paymentMetrics).recordDuplicate();
        verify(paymentProducer, never()).publish(any());
    }

    @Test
    void rejectsInvalidCharge() throws Exception {
        CreatePaymentRequest body = new CreatePaymentRequest("", 0, "");

        mockMvc.perform(post("/api/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }
}
