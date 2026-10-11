package com.finstack.transaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finstack.account.AccountType;
import com.finstack.account.FinancialAccount;
import com.finstack.budget.BudgetEvaluationService;
import com.finstack.event.TransactionEventPublisher;
import com.finstack.transaction.dto.TransactionCreateRequest;
import com.finstack.user.User;
import com.finstack.user.UserStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({TransactionService.class, IdempotentTransactionCreationService.class, TransactionIdempotencyPersistenceTest.JacksonConfig.class})
class TransactionIdempotencyPersistenceTest {
    @TestConfiguration
    static class JacksonConfig {
        @Bean ObjectMapper objectMapper() { return new ObjectMapper().findAndRegisterModules(); }
    }

    @Autowired private EntityManager entityManager;
    @Autowired private TransactionRepository transactions;
    @Autowired private TransactionCreateIdempotencyRepository records;
    @Autowired private IdempotentTransactionCreationService creation;
    @MockitoBean private BudgetEvaluationService budgets;
    @MockitoBean private TransactionEventPublisher events;

    @AfterEach void clearSecurityContext() { SecurityContextHolder.clearContext(); }

    @Test
    void retryWithSameKeyReplaysOriginalResponseWithoutSecondBalanceEffect() {
        FinancialAccount account = account("retry-owner@example.com");
        TransactionCreateRequest request = request(account, "125.00");

        var first = creation.create(request, "retry-1");
        var replay = creation.create(request, "retry-1");
        entityManager.flush();

        assertThat(replay).isEqualTo(first);
        assertThat(transactions.count()).isEqualTo(1);
        assertThat(records.count()).isEqualTo(1);
        assertThat(account.getCurrentBalance()).isEqualByComparingTo("1125.00");
    }

    @Test
    void changedPayloadWithSameKeyConflictsWithoutSecondBalanceEffect() {
        FinancialAccount account = account("conflict-owner@example.com");
        creation.create(request(account, "125.00"), "retry-2");

        assertThatThrownBy(() -> creation.create(request(account, "250.00"), "retry-2"))
                .isInstanceOf(IdempotencyKeyConflictException.class);
        assertThat(transactions.count()).isEqualTo(1);
        assertThat(account.getCurrentBalance()).isEqualByComparingTo("1125.00");
    }

    @Test
    void sameKeyIsScopedToOwnerAndLegacyRequestsRemainUnkeyed() {
        FinancialAccount firstAccount = account("first-owner@example.com");
        creation.create(request(firstAccount, "10.00"), "shared-key");
        FinancialAccount secondAccount = account("second-owner@example.com");
        creation.create(request(secondAccount, "20.00"), "shared-key");
        creation.create(request(secondAccount, "20.00"), null);

        assertThat(transactions.count()).isEqualTo(3);
        assertThat(records.count()).isEqualTo(2);
        assertThat(firstAccount.getCurrentBalance()).isEqualByComparingTo("1010.00");
        assertThat(secondAccount.getCurrentBalance()).isEqualByComparingTo("1040.00");
    }

    @Test
    void rejectsInvalidKeysBeforeCreatingTransaction() {
        FinancialAccount account = account("invalid-key-owner@example.com");
        assertThatThrownBy(() -> creation.create(request(account, "10.00"), " "))
                .isInstanceOf(InvalidIdempotencyKeyException.class);
        assertThat(transactions.count()).isZero();
    }

    @Test
    void rejectedExpenseDoesNotConsumeKeyOrChangeBalance() {
        FinancialAccount account = account("rejected-owner@example.com");
        TransactionCreateRequest expense = new TransactionCreateRequest(account.getId(), null,
                TransactionType.EXPENSE, new BigDecimal("1500.00"), null, "Too large", null,
                OffsetDateTime.parse("2026-10-05T10:00:00Z"), TransactionStatus.COMPLETED, null);

        assertThatThrownBy(() -> creation.create(expense, "rejected-1"))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(transactions.count()).isZero();
        assertThat(records.count()).isZero();
        assertThat(account.getCurrentBalance()).isEqualByComparingTo("1000.00");
    }

    private FinancialAccount account(String email) {
        User user = new User("QA", "Owner", email, "hash", UserStatus.ACTIVE);
        entityManager.persist(user);
        FinancialAccount account = new FinancialAccount(user, "Savings", AccountType.SAVINGS_ACCOUNT,
                null, "INR", new BigDecimal("1000.00"), null);
        entityManager.persist(account);
        entityManager.flush();
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(email, null, List.of()));
        return account;
    }

    private TransactionCreateRequest request(FinancialAccount account, String amount) {
        return new TransactionCreateRequest(account.getId(), null, TransactionType.INCOME,
                new BigDecimal(amount), null, "QA income", null,
                OffsetDateTime.parse("2026-10-05T10:00:00Z"), TransactionStatus.COMPLETED, null);
    }
}
