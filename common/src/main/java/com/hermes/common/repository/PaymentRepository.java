package com.hermes.common.repository;

import com.hermes.common.domain.Payment;
import com.hermes.common.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    long countByStatus(PaymentStatus status);

    @Query("select coalesce(sum(p.amountCents), 0) from Payment p where p.status = :status")
    long sumAmountByStatus(@Param("status") PaymentStatus status);
}
