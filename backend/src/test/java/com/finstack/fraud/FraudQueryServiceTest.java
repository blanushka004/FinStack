package com.finstack.fraud;

import com.finstack.account.AccountType;
import com.finstack.account.FinancialAccount;
import com.finstack.category.CategoryType;
import com.finstack.category.TransactionCategory;
import com.finstack.fraud.dto.FraudAssessmentResponse;
import com.finstack.fraud.dto.FraudSummaryResponse;
import com.finstack.transaction.Transaction;
import com.finstack.transaction.TransactionStatus;
import com.finstack.transaction.TransactionType;
import com.finstack.user.User;
import com.finstack.user.UserRepository;
import com.finstack.user.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FraudQueryServiceTest {
    @Mock private FraudAssessmentRepository assessments;
    @Mock private UserRepository users;
    private FraudQueryService service;
    private User owner;
    private FraudAssessment assessment;

    @BeforeEach
    void setUp() {
        service = new FraudQueryService(assessments, users);
        owner = new User("Ada", "Lovelace", "ada@example.com", "hash", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(owner, "id", UUID.randomUUID());
        TransactionCategory category = new TransactionCategory("FOOD", CategoryType.EXPENSE, owner);
        FinancialAccount account = new FinancialAccount(owner, "Main", AccountType.BANK_ACCOUNT, null, "INR", new BigDecimal("5000"), null);
        Transaction transaction = new Transaction(account, category, TransactionType.EXPENSE, new BigDecimal("425.50"),
                "Coffee Shop", "Coffee", null, OffsetDateTime.parse("2026-09-01T10:00:00Z"), TransactionStatus.COMPLETED, null);
        assessment = new FraudAssessment(UUID.randomUUID(), transaction, owner, 45, RiskLevel.MEDIUM, false, "LARGE_TRANSACTION");
        ReflectionTestUtils.setField(transaction, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(assessment, "id", UUID.randomUUID());
        when(users.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(owner));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("ada@example.com", null, List.of())
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void mapsAssessmentTransactionFieldsInsideTheReadOnlyQueryBoundaryAndPreservesPageMetadata() {
        PageRequest pageable = PageRequest.of(1, 2);
        when(assessments.findAllByUserId(owner.getId(), pageable)).thenReturn(new PageImpl<>(List.of(assessment), pageable, 5));

        Page<FraudAssessmentResponse> result = service.list(pageable);

        FraudAssessmentResponse response = result.getContent().getFirst();
        assertThat(response.transactionId()).isEqualTo(assessment.getTransaction().getId());
        assertThat(response.amount()).isEqualByComparingTo("425.50");
        assertThat(response.merchant()).isEqualTo("Coffee Shop");
        assertThat(response.categoryName()).isEqualTo("FOOD");
        assertThat(response.reasons()).containsExactly("LARGE_TRANSACTION");
        assertThat(result.getTotalElements()).isEqualTo(5);
        assertThat(result.getNumber()).isEqualTo(1);
    }

    @Test
    void transactionSpecificLookupIsOwnershipScoped() {
        UUID transactionId = assessment.getTransaction().getId();
        when(assessments.findTopByTransactionIdAndUserIdOrderByEvaluatedAtDesc(transactionId, owner.getId()))
                .thenReturn(Optional.of(assessment));

        FraudAssessmentResponse response = service.byTransaction(transactionId);

        assertThat(response.id()).isEqualTo(assessment.getId());
        verify(assessments).findTopByTransactionIdAndUserIdOrderByEvaluatedAtDesc(transactionId, owner.getId());
    }

    @Test
    void foreignAssessmentIsNotExposed() {
        UUID foreignTransactionId = UUID.randomUUID();
        when(assessments.findTopByTransactionIdAndUserIdOrderByEvaluatedAtDesc(foreignTransactionId, owner.getId()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.byTransaction(foreignTransactionId))
                .isInstanceOf(FraudAssessmentNotFoundException.class);
    }

    @Test
    void summaryUsesOnlyCurrentUsersCounts() {
        when(assessments.countByUserIdAndRiskLevel(owner.getId(), RiskLevel.LOW)).thenReturn(2L);
        when(assessments.countByUserIdAndRiskLevel(owner.getId(), RiskLevel.MEDIUM)).thenReturn(3L);
        when(assessments.countByUserIdAndRiskLevel(owner.getId(), RiskLevel.HIGH)).thenReturn(1L);
        when(assessments.countByUserIdAndSuspiciousTrue(owner.getId())).thenReturn(1L);

        FraudSummaryResponse response = service.summary();

        assertThat(response).isEqualTo(new FraudSummaryResponse(2, 3, 1, 1));
    }
}
