package com.finstack.fraud;

import com.finstack.account.AccountType;
import com.finstack.account.FinancialAccount;
import com.finstack.category.CategoryType;
import com.finstack.category.TransactionCategory;
import com.finstack.fraud.dto.FraudAssessmentResponse;
import com.finstack.transaction.Transaction;
import com.finstack.transaction.TransactionStatus;
import com.finstack.transaction.TransactionType;
import com.finstack.user.User;
import com.finstack.user.UserRepository;
import com.finstack.user.UserStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class FraudQueryPersistenceTest {

    @Autowired private EntityManager entityManager;
    @Autowired private FraudAssessmentRepository assessments;
    @Autowired private UserRepository users;

    private FraudQueryService fraudQueries;
    private User owner;
    private UUID ownerTransactionId;
    private UUID foreignTransactionId;

    @BeforeEach
    void setUp() {
        owner = persistUser("ada@example.com");
        ownerTransactionId = persistAssessment(owner, "Coffee Shop", RiskLevel.MEDIUM).getTransaction().getId();
        foreignTransactionId = persistAssessment(persistUser("grace@example.com"), "Foreign Merchant", RiskLevel.HIGH)
                .getTransaction().getId();

        entityManager.flush();
        entityManager.clear();
        fraudQueries = new FraudQueryService(assessments, users);
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(owner.getEmail(), null, List.of())
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void paginatedListingFetchesTheLazyTransactionAndCategoryBeforeDtoMapping() {
        Page<FraudAssessmentResponse> page = fraudQueries.list(PageRequest.of(0, 1));

        assertThat(page.getTotalElements()).isEqualTo(1);
        FraudAssessmentResponse response = page.getContent().getFirst();
        assertThat(response.transactionId()).isEqualTo(ownerTransactionId);
        assertThat(response.amount()).isEqualByComparingTo("425.50");
        assertThat(response.merchant()).isEqualTo("Coffee Shop");
        assertThat(response.categoryName()).isEqualTo("FOOD");
    }

    @Test
    void transactionLookupAndSummaryRemainOwnerScoped() {
        FraudAssessmentResponse response = fraudQueries.byTransaction(ownerTransactionId);

        assertThat(response.transactionId()).isEqualTo(ownerTransactionId);
        assertThat(fraudQueries.summary().mediumRisk()).isEqualTo(1);
        assertThatThrownBy(() -> fraudQueries.byTransaction(foreignTransactionId))
                .isInstanceOf(FraudAssessmentNotFoundException.class);
    }

    private User persistUser(String email) {
        User user = new User("Test", "User", email, "hash", UserStatus.ACTIVE);
        entityManager.persist(user);
        return user;
    }

    private FraudAssessment persistAssessment(User user, String merchant, RiskLevel riskLevel) {
        FinancialAccount account = new FinancialAccount(user, "Main", AccountType.BANK_ACCOUNT, null,
                "INR", new BigDecimal("5000.00"), null);
        entityManager.persist(account);
        TransactionCategory category = new TransactionCategory("FOOD", CategoryType.EXPENSE, user);
        entityManager.persist(category);
        Transaction transaction = new Transaction(account, category, TransactionType.EXPENSE,
                new BigDecimal("425.50"), merchant, "Coffee", null,
                OffsetDateTime.parse("2026-09-01T10:00:00Z"), TransactionStatus.COMPLETED, null);
        entityManager.persist(transaction);
        FraudAssessment assessment = new FraudAssessment(UUID.randomUUID(), transaction, user, 45,
                riskLevel, riskLevel == RiskLevel.HIGH, "LARGE_TRANSACTION");
        entityManager.persist(assessment);
        return assessment;
    }
}
