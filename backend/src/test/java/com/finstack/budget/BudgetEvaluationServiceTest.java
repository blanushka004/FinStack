package com.finstack.budget;

import com.finstack.alert.AlertRepository;
import com.finstack.alert.AlertType;
import com.finstack.category.CategoryType;
import com.finstack.category.TransactionCategory;
import com.finstack.transaction.TransactionRepository;
import com.finstack.user.User;
import com.finstack.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetEvaluationServiceTest {

    @Mock private BudgetRepository budgetRepository;
    @Mock private TransactionRepository transactionRepository;
    @Mock private AlertRepository alertRepository;

    @Test
    void createsWarningOnceWhenThresholdIsReached() {
        User user = user();
        Budget budget = budget(user, "1000.00", 80);
        when(budgetRepository.findAllByUserIdAndBudgetMonthAndActive(eq(user.getId()), any(LocalDate.class), eq(true)))
                .thenReturn(List.of(budget));
        when(transactionRepository.totalCompletedExpenseForMonth(any(), any(), any())).thenReturn(new BigDecimal("800.00"));
        when(alertRepository.existsByRelatedBudgetIdAndAlertType(budget.getId(), AlertType.BUDGET_WARNING)).thenReturn(false);

        new BudgetEvaluationService(budgetRepository, transactionRepository, alertRepository)
                .evaluate(user, OffsetDateTime.parse("2026-09-12T12:00:00Z"));

        verify(alertRepository).save(any());
        verify(alertRepository, never()).existsByRelatedBudgetIdAndAlertType(budget.getId(), AlertType.BUDGET_EXCEEDED);
    }

    @Test
    void createsExceededAlertWithoutRepeatingExistingWarning() {
        User user = user();
        Budget budget = budget(user, "1000.00", 80);
        when(budgetRepository.findAllByUserIdAndBudgetMonthAndActive(eq(user.getId()), any(LocalDate.class), eq(true)))
                .thenReturn(List.of(budget));
        when(transactionRepository.totalCompletedExpenseForMonth(any(), any(), any())).thenReturn(new BigDecimal("1001.00"));
        when(alertRepository.existsByRelatedBudgetIdAndAlertType(budget.getId(), AlertType.BUDGET_WARNING)).thenReturn(true);
        when(alertRepository.existsByRelatedBudgetIdAndAlertType(budget.getId(), AlertType.BUDGET_EXCEEDED)).thenReturn(false);

        new BudgetEvaluationService(budgetRepository, transactionRepository, alertRepository)
                .evaluate(user, OffsetDateTime.parse("2026-09-12T12:00:00Z"));

        verify(alertRepository).save(any());
    }

    @Test
    void createsOneCategoryWarningWhenAnAllocationReachesItsThreshold() {
        User user = user();
        Budget budget = budget(user, "1000.00", 80);
        TransactionCategory food = new TransactionCategory("FOOD", CategoryType.EXPENSE, null);
        ReflectionTestUtils.setField(food, "id", UUID.randomUUID());
        ReflectionTestUtils.setField(food, "systemDefined", true);
        budget.addCategory(new BudgetCategory(food, new BigDecimal("100.00")));
        when(budgetRepository.findAllByUserIdAndBudgetMonthAndActive(eq(user.getId()), any(LocalDate.class), eq(true)))
                .thenReturn(List.of(budget));
        when(transactionRepository.totalCompletedExpenseForMonth(any(), any(), any())).thenReturn(new BigDecimal("100.00"));
        when(transactionRepository.categoryCompletedExpenseForMonth(any(), eq(food.getId()), any(), any()))
                .thenReturn(new BigDecimal("80.00"));
        when(alertRepository.existsByRelatedBudgetIdAndRelatedCategoryIdAndAlertType(
                budget.getId(), food.getId(), AlertType.CATEGORY_BUDGET_WARNING)).thenReturn(false);

        new BudgetEvaluationService(budgetRepository, transactionRepository, alertRepository)
                .evaluate(user, OffsetDateTime.parse("2026-09-12T12:00:00Z"));

        verify(alertRepository).save(any());
    }

    private User user() {
        User user = new User("Ada", "Lovelace", "ada@example.com", "hash", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        return user;
    }

    private Budget budget(User user, String limit, int threshold) {
        Budget budget = new Budget(user, "September", LocalDate.of(2026, 9, 1), new BigDecimal(limit), threshold);
        ReflectionTestUtils.setField(budget, "id", UUID.randomUUID());
        return budget;
    }
}
