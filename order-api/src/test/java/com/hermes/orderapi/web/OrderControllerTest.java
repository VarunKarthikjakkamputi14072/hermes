package com.hermes.orderapi.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hermes.common.repository.OrderRepository;
import com.hermes.orderapi.kafka.OrderProducer;
import com.hermes.orderapi.web.dto.CreateOrderRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.hermes.common.event.OrderPlacedEvent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    @MockBean
    OrderRepository orderRepository;
    @MockBean
    OrderProducer orderProducer;

    @Test
    void acceptsValidOrderAndPublishesEvent() throws Exception {
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CreateOrderRequest body = new CreateOrderRequest("cust-1", "SKU-1", 2);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.sku").value("SKU-1"));

        ArgumentCaptor<OrderPlacedEvent> captor = ArgumentCaptor.forClass(OrderPlacedEvent.class);
        verify(orderProducer).publish(captor.capture());
        assertThat(captor.getValue().quantity()).isEqualTo(2);
    }

    @Test
    void rejectsInvalidOrder() throws Exception {
        CreateOrderRequest body = new CreateOrderRequest("", "SKU-1", 0);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }
}
