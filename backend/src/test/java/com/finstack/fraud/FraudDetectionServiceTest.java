package com.finstack.fraud;

import com.finstack.account.AccountType;
import com.finstack.account.FinancialAccount;
import com.finstack.alert.AlertRepository;
import com.finstack.event.TransactionEvent;
import com.finstack.event.TransactionEventType;
import com.finstack.transaction.Transaction;
import com.finstack.transaction.TransactionRepository;
import com.finstack.transaction.TransactionStatus;
import com.finstack.transaction.TransactionType;
import com.finstack.user.User;
import com.finstack.user.UserRepository;
import com.finstack.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FraudDetectionServiceTest {
    @Mock private FraudAssessmentRepository assessments;
    @Mock private TransactionRepository transactions;
    @Mock private UserRepository users;
    @Mock private AlertRepository alerts;
    private FraudDetectionService service;
    private User user;
    private Transaction transaction;

    @BeforeEach
    void setUp() {
        service = new FraudDetectionService(assessments, transactions, users, alerts);
        user = new User("Ada", "Lovelace", "ada@example.com", "hash", UserStatus.ACTIVE);
        FinancialAccount account = new FinancialAccount(user, "Main", AccountType.BANK_ACCOUNT, null, "INR", new BigDecimal("1000"), null);
        transaction = new Transaction(account, null, TransactionType.EXPENSE, new BigDecimal("10"), "Shop", null, null, OffsetDateTime.now(), TransactionStatus.COMPLETED, null);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(transaction, "id", UUID.randomUUID());
        when(assessments.existsByEventId(any())).thenReturn(false);
        lenient().when(transactions.findById(transaction.getId())).thenReturn(Optional.of(transaction));
        lenient().when(users.findById(user.getId())).thenReturn(Optional.of(user));
        lenient().when(assessments.save(any(FraudAssessment.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void normalCompletedTransactionReceivesLowRiskAssessment() {
        service.assess(event(new BigDecimal("10"), OffsetDateTime.now(ZoneOffset.UTC)));
        ArgumentCaptor<FraudAssessment> captor = ArgumentCaptor.forClass(FraudAssessment.class);
        verify(assessments).save(captor.capture());
        assertThat(captor.getValue().getRiskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(captor.getValue().getRiskScore()).isZero();
        verifyNoInteractions(alerts);
    }

    @Test
    void largeOddHourTransactionIsHighRiskAndCreatesOneAlert() {
        service.assess(event(new BigDecimal("100000"), OffsetDateTime.now(ZoneOffset.UTC).withHour(2)));
        ArgumentCaptor<FraudAssessment> captor = ArgumentCaptor.forClass(FraudAssessment.class);
        verify(assessments).save(captor.capture());
        assertThat(captor.getValue().getRiskLevel()).isEqualTo(RiskLevel.HIGH);
        assertThat(captor.getValue().getRiskScore()).isGreaterThanOrEqualTo(60).isLessThanOrEqualTo(100);
        verify(alerts).save(any());
    }

    @Test
    void highRiskTransactionDoesNotCreateDuplicateFraudAlert() {
        when(alerts.existsByRelatedTransactionIdAndAlertType(any(), any())).thenReturn(true);

        service.assess(event(new BigDecimal("100000"), OffsetDateTime.now(ZoneOffset.UTC).withHour(2)));

        verify(assessments).save(any(FraudAssessment.class));
        verify(alerts, never()).save(any());
    }

    @Test
    void duplicateKafkaEventIsIgnored() {
        when(assessments.existsByEventId(any())).thenReturn(true);
        service.assess(event(BigDecimal.TEN, OffsetDateTime.now(ZoneOffset.UTC)));
        verify(assessments, never()).save(any());
        verifyNoInteractions(alerts);
    }

    @Test
    void delayedCreateEventForNowCancelledTransactionDoesNotCreateAssessmentOrAlert() {
        transaction.cancel();

        service.assess(event(new BigDecimal("100000"), OffsetDateTime.now(ZoneOffset.UTC).withHour(2)));

        verify(assessments, never()).save(any());
        verifyNoInteractions(alerts);
    }

    @Test
    void mismatchedEventOwnerDoesNotCreateAssessmentOrAlert() {
        TransactionEvent valid = event(new BigDecimal("100000"), OffsetDateTime.now(ZoneOffset.UTC).withHour(2));
        TransactionEvent mismatched = new TransactionEvent(valid.eventId(), valid.eventType(), valid.occurredAt(),
                valid.transactionId(), UUID.randomUUID(), valid.accountId(), valid.categoryId(),
                valid.transactionType(), valid.amount(), valid.merchant(), valid.transactionTime(), valid.status());
        User other = new User("Other", "User", "other@example.com", "hash", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(other, "id", mismatched.userId());
        when(users.findById(mismatched.userId())).thenReturn(Optional.of(other));

        service.assess(mismatched);

        verify(assessments, never()).save(any());
        verifyNoInteractions(alerts);
    }

    private TransactionEvent event(BigDecimal amount, OffsetDateTime time) {
        return new TransactionEvent(UUID.randomUUID(), TransactionEventType.TRANSACTION_CREATED, OffsetDateTime.now(ZoneOffset.UTC),
                transaction.getId(), user.getId(), transaction.getAccount().getId(), null, TransactionType.EXPENSE,
                amount, "Shop", time, TransactionStatus.COMPLETED);
    }
}
