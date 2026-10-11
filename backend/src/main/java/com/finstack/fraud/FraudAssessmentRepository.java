package com.finstack.fraud;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface FraudAssessmentRepository extends JpaRepository<FraudAssessment, UUID> {
    boolean existsByEventId(UUID eventId);

    @EntityGraph(attributePaths = {"transaction", "transaction.category"})
    Optional<FraudAssessment> findTopByTransactionIdAndUserIdOrderByEvaluatedAtDesc(UUID transactionId, UUID userId);

    @EntityGraph(attributePaths = {"transaction", "transaction.category"})
    Page<FraudAssessment> findAllByUserId(UUID userId, Pageable pageable);

    long countByUserIdAndRiskLevel(UUID userId, RiskLevel level);

    long countByUserIdAndSuspiciousTrue(UUID userId);
}
