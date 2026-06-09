package com.hermes.common.repository;

import com.hermes.common.domain.OrderEntity;
import com.hermes.common.domain.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {

    long countByStatus(OrderStatus status);
}
