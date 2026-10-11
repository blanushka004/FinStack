package com.finstack.fraud.dto;

import com.finstack.fraud.FraudAssessment;
import com.finstack.fraud.RiskLevel;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record FraudAssessmentResponse(UUID id, UUID transactionId, BigDecimal amount, String merchant,
        String categoryName, int riskScore, RiskLevel riskLevel, boolean suspicious,
        List<String> reasons, OffsetDateTime evaluatedAt) {
    public static FraudAssessmentResponse from(FraudAssessment assessment) {
        var transaction = assessment.getTransaction();
        return new FraudAssessmentResponse(assessment.getId(), transaction.getId(), transaction.getAmount(),
                transaction.getMerchant(), transaction.getCategory() == null ? null : transaction.getCategory().getName(),
                assessment.getRiskScore(), assessment.getRiskLevel(), assessment.isSuspicious(),
                assessment.getReasons().isBlank() ? List.of() : List.of(assessment.getReasons().split(",")),
                assessment.getEvaluatedAt());
    }
}
