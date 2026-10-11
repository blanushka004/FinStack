package com.finstack.fraud;

import com.finstack.alert.Alert;
import com.finstack.alert.AlertRepository;
import com.finstack.alert.AlertSeverity;
import com.finstack.alert.AlertType;
import com.finstack.event.TransactionEvent;
import com.finstack.event.TransactionEventType;
import com.finstack.transaction.Transaction;
import com.finstack.transaction.TransactionRepository;
import com.finstack.transaction.TransactionStatus;
import com.finstack.transaction.TransactionType;
import com.finstack.user.User;
import com.finstack.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class FraudDetectionService {
    private static final Logger log = LoggerFactory.getLogger(FraudDetectionService.class);
    private static final BigDecimal LARGE_TRANSACTION = new BigDecimal("100000");
    private static final BigDecimal UTILIZATION_PERCENT = new BigDecimal("0.75");
    private static final int LARGE_TRANSACTION_WEIGHT = 45;
    private static final int ODD_HOUR_WEIGHT = 15;
    private static final int HIGH_UTILIZATION_WEIGHT = 25;
    private static final int MEDIUM_THRESHOLD = 30;
    private static final int HIGH_THRESHOLD = 60;

    private final FraudAssessmentRepository assessments;
    private final TransactionRepository transactions;
    private final UserRepository users;
    private final AlertRepository alerts;

    public FraudDetectionService(FraudAssessmentRepository assessments, TransactionRepository transactions,
                                 UserRepository users, AlertRepository alerts) {
        this.assessments = assessments;
        this.transactions = transactions;
        this.users = users;
        this.alerts = alerts;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void assess(TransactionEvent event) {
        if (assessments.existsByEventId(event.eventId())) {
            return; // Kafka is at-least-once; event_id is the durable idempotency key.
        }
        if (event.eventType() == TransactionEventType.TRANSACTION_CANCELLED || event.status() != TransactionStatus.COMPLETED) {
            return;
        }
        Transaction transaction = transactions.findById(event.transactionId()).orElse(null);
        User user = users.findById(event.userId()).orElse(null);
        if (transaction == null || user == null) {
            log.warn("Skipping fraud event {} because its transaction or user no longer exists", event.eventId());
            return;
        }
        if (transaction.getStatus() != TransactionStatus.COMPLETED
                || !event.accountId().equals(transaction.getAccount().getId())
                || !user.getId().equals(transaction.getAccount().getUser().getId())) {
            log.info("Skipping stale or ownership-mismatched fraud event {}", event.eventId());
            return;
        }

        int score = 0;
        List<String> reasons = new ArrayList<>();
        if (event.amount().compareTo(LARGE_TRANSACTION) >= 0) {
            score += LARGE_TRANSACTION_WEIGHT;
            reasons.add("LARGE_TRANSACTION");
        }
        int hour = event.transactionTime().getHour();
        if (hour >= 0 && hour < 5) {
            score += ODD_HOUR_WEIGHT;
            reasons.add("ODD_HOUR_TRANSACTION");
        }
        BigDecimal postTransactionBalance = transaction.getAccount().getCurrentBalance().abs();
        if (event.transactionType() == TransactionType.EXPENSE
                && postTransactionBalance.signum() > 0
                && event.amount().compareTo(postTransactionBalance.multiply(UTILIZATION_PERCENT)) > 0) {
            score += HIGH_UTILIZATION_WEIGHT;
            reasons.add("HIGH_ACCOUNT_UTILIZATION");
        }
        score = Math.min(100, score);
        RiskLevel riskLevel = score >= HIGH_THRESHOLD ? RiskLevel.HIGH
                : score >= MEDIUM_THRESHOLD ? RiskLevel.MEDIUM : RiskLevel.LOW;
        FraudAssessment assessment = assessments.save(new FraudAssessment(
                event.eventId(), transaction, user, score, riskLevel, riskLevel == RiskLevel.HIGH,
                String.join(",", reasons)
        ));
        if (assessment.isSuspicious()
                && !alerts.existsByRelatedTransactionIdAndAlertType(transaction.getId(), AlertType.FRAUD_SUSPECTED)) {
            alerts.save(Alert.forTransaction(user, AlertType.FRAUD_SUSPECTED, AlertSeverity.HIGH,
                    "Suspicious transaction detected",
                    "A completed transaction was assessed as high risk (score " + assessment.getRiskScore() + ").",
                    transaction));
        }
    }
}
