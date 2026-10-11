package com.finstack.fraud;

import com.finstack.fraud.dto.FraudAssessmentResponse;
import com.finstack.fraud.dto.FraudSummaryResponse;
import com.finstack.user.User;
import com.finstack.user.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class FraudQueryService {
    private final FraudAssessmentRepository assessments;
    private final UserRepository users;

    public FraudQueryService(FraudAssessmentRepository assessments, UserRepository users) {
        this.assessments = assessments;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public Page<FraudAssessmentResponse> list(Pageable pageable) {
        return assessments.findAllByUserId(currentUser().getId(), pageable).map(FraudAssessmentResponse::from);
    }

    @Transactional(readOnly = true)
    public FraudAssessmentResponse byTransaction(UUID transactionId) {
        UUID userId = currentUser().getId();
        return assessments.findTopByTransactionIdAndUserIdOrderByEvaluatedAtDesc(transactionId, userId)
                .map(FraudAssessmentResponse::from)
                .orElseThrow(() -> new FraudAssessmentNotFoundException(transactionId));
    }

    @Transactional(readOnly = true)
    public FraudSummaryResponse summary() {
        UUID userId = currentUser().getId();
        return new FraudSummaryResponse(
                assessments.countByUserIdAndRiskLevel(userId, RiskLevel.LOW),
                assessments.countByUserIdAndRiskLevel(userId, RiskLevel.MEDIUM),
                assessments.countByUserIdAndRiskLevel(userId, RiskLevel.HIGH),
                assessments.countByUserIdAndSuspiciousTrue(userId)
        );
    }

    private User currentUser() {
        return users.findByEmailIgnoreCase(SecurityContextHolder.getContext().getAuthentication().getName())
                .orElseThrow(() -> new IllegalStateException("Authenticated user no longer exists"));
    }
}
