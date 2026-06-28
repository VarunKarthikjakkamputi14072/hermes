package com.hermes.common.repository;

import com.hermes.common.domain.FraudFlag;
import com.hermes.common.domain.RiskDecision;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FraudFlagRepository extends JpaRepository<FraudFlag, UUID> {

    List<FraudFlag> findByNarrativePendingTrueOrderByCreatedAtAsc(Pageable pageable);

    List<FraudFlag> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByDecision(RiskDecision decision);
}
