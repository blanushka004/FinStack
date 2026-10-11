package com.finstack.dashboard;

import com.finstack.account.FinancialAccountRepository;
import com.finstack.alert.AlertRepository;
import com.finstack.budget.BudgetRepository;
import com.finstack.dashboard.dto.DashboardAnalyticsResponse;
import com.finstack.dashboard.dto.DashboardSummaryResponse;
import com.finstack.transaction.TransactionRepository;
import com.finstack.transaction.TransactionType;
import com.finstack.transaction.projection.CategorySpendingProjection;
import com.finstack.user.User;
import com.finstack.user.UserRepository;
import com.finstack.user.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {
    @Mock private FinancialAccountRepository accounts;
    @Mock private TransactionRepository transactions;
    @Mock private BudgetRepository budgets;
    @Mock private AlertRepository alerts;
    @Mock private UserRepository users;

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void calculatesOnlyTheAuthenticatedUsersMonthlySummary() {
        User user = authenticatedUser();
        UUID id = user.getId();
        when(accounts.totalActiveBalanceByUserId(id)).thenReturn(new BigDecimal("1250.00"));
        when(transactions.totalCompletedByUserAndTypeBetween(eq(id), eq(TransactionType.INCOME), any(), any())).thenReturn(new BigDecimal("900.00"));
        when(transactions.totalCompletedByUserAndTypeBetween(eq(id), eq(TransactionType.EXPENSE), any(), any())).thenReturn(new BigDecimal("400.00"));
        when(accounts.countByUserIdAndActive(id, true)).thenReturn(2L);
        when(budgets.countByUserIdAndActive(id, true)).thenReturn(1L);
        when(alerts.countByUserIdAndRead(id, false)).thenReturn(3L);

        DashboardSummaryResponse result = service().summary();

        assertThat(result.netCashFlow()).isEqualByComparingTo("500.00");
        assertThat(result.totalBalance()).isEqualByComparingTo("1250.00");
        assertThat(result.activeAccounts()).isEqualTo(2);
    }

    @Test
    void analyticsUsesOnlyCurrentUserCompletedAggregationResults() {
        User user = authenticatedUser();
        UUID categoryId = UUID.randomUUID();
        OffsetDateTime month = OffsetDateTime.now(ZoneOffset.UTC).withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0).withNano(0);
        when(transactions.totalCompletedByUserAndTypeBetween(eq(user.getId()), any(), any(), any()))
                .thenAnswer(invocation -> {
                    TransactionType type = invocation.getArgument(1);
                    OffsetDateTime start = invocation.getArgument(2);
                    if (!start.equals(month)) return BigDecimal.ZERO;
                    return type == TransactionType.INCOME ? new BigDecimal("1500.00") : new BigDecimal("425.50");
                });
        when(transactions.currentMonthCompletedExpenseByCategory(eq(user.getId()), any(), any()))
                .thenReturn(List.of(category(categoryId, "FOOD", "425.50")));

        DashboardAnalyticsResponse result = service().analytics();

        DashboardAnalyticsResponse.MonthlyCashFlow current = result.cashFlowTrend().getLast();
        assertThat(result.cashFlowTrend()).hasSize(6);
        assertThat(current.income()).isEqualByComparingTo("1500.00");
        assertThat(current.expenses()).isEqualByComparingTo("425.50");
        assertThat(current.netCashFlow()).isEqualByComparingTo("1074.50");
        assertThat(result.categorySpending()).containsExactly(
                new DashboardAnalyticsResponse.CategorySpending(categoryId, "FOOD", new BigDecimal("425.50"))
        );
        verify(transactions, times(12)).totalCompletedByUserAndTypeBetween(eq(user.getId()), any(), any(), any());
        verify(transactions).currentMonthCompletedExpenseByCategory(eq(user.getId()), any(), any());
    }

    private DashboardService service() {
        return new DashboardService(accounts, transactions, budgets, alerts, users);
    }

    private User authenticatedUser() {
        User user = new User("Ada", "Lovelace", "ada@example.com", "hash", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user.getEmail(), null, List.of())
        );
        when(users.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        return user;
    }

    private CategorySpendingProjection category(UUID id, String name, String amount) {
        return new CategorySpendingProjection() {
            @Override public UUID getCategoryId() { return id; }
            @Override public String getCategoryName() { return name; }
            @Override public BigDecimal getAmount() { return new BigDecimal(amount); }
        };
    }
}
