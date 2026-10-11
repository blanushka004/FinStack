package com.finstack.event;

import com.finstack.account.AccountType;
import com.finstack.account.FinancialAccount;
import com.finstack.fraud.FraudDetectionService;
import com.finstack.transaction.Transaction;
import com.finstack.transaction.TransactionStatus;
import com.finstack.transaction.TransactionType;
import com.finstack.user.User;
import com.finstack.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class TransactionEventPublisherTest {
    @Test
    void usesLocalFraudAssessmentFallbackWhenKafkaIsDisabled() {
        @SuppressWarnings("unchecked") ObjectProvider<KafkaTemplate<String, TransactionEvent>> provider = mock(ObjectProvider.class);
        FraudDetectionService fraudDetectionService = mock(FraudDetectionService.class);
        TransactionEventPublisher publisher = new TransactionEventPublisher(
                new KafkaProperties(false, "finstack.transaction.events", "finstack.fraud.results"), provider, fraudDetectionService
        );

        publisher.afterCommit(transaction(), TransactionEventType.TRANSACTION_CREATED);

        verify(provider).getIfAvailable();
        verify(fraudDetectionService).assess(org.mockito.ArgumentMatchers.any(TransactionEvent.class));
    }

    private Transaction transaction() {
        User user = new User("Ada", "Lovelace", "ada@example.com", "hash", UserStatus.ACTIVE);
        FinancialAccount account = new FinancialAccount(user, "Main", AccountType.BANK_ACCOUNT, null, "INR", BigDecimal.ZERO, null);
        Transaction transaction = new Transaction(account, null, TransactionType.EXPENSE, BigDecimal.TEN,
                "Merchant", null, null, OffsetDateTime.now(), TransactionStatus.COMPLETED, null);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(transaction, "id", UUID.randomUUID());
        return transaction;
    }
}
