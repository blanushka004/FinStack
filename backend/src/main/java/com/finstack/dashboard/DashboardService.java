package com.finstack.dashboard;

import com.finstack.account.FinancialAccountRepository;
import com.finstack.alert.AlertRepository;
import com.finstack.budget.BudgetRepository;
import com.finstack.dashboard.dto.DashboardAnalyticsResponse;
import com.finstack.dashboard.dto.DashboardSummaryResponse;
import com.finstack.transaction.TransactionRepository;
import com.finstack.transaction.TransactionType;
import com.finstack.user.User;
import com.finstack.user.UserRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Service
public class DashboardService {
    private final FinancialAccountRepository accounts;
    private final TransactionRepository transactions;
    private final BudgetRepository budgets;
    private final AlertRepository alerts;
    private final UserRepository users;

    public DashboardService(FinancialAccountRepository accounts, TransactionRepository transactions,
                            BudgetRepository budgets, AlertRepository alerts, UserRepository users) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.budgets = budgets;
        this.alerts = alerts;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse summary() {
        User user = currentUser();
        LocalDate month = currentMonth();
        OffsetDateTime start = startOf(month);
        OffsetDateTime end = startOf(month.plusMonths(1));
        BigDecimal income = zero(transactions.totalCompletedByUserAndTypeBetween(user.getId(), TransactionType.INCOME, start, end));
        BigDecimal expenses = zero(transactions.totalCompletedByUserAndTypeBetween(user.getId(), TransactionType.EXPENSE, start, end));
        return new DashboardSummaryResponse(
                zero(accounts.totalActiveBalanceByUserId(user.getId())), income, expenses, income.subtract(expenses),
                accounts.countByUserIdAndActive(user.getId(), true), budgets.countByUserIdAndActive(user.getId(), true),
                alerts.countByUserIdAndRead(user.getId(), false)
        );
    }

    @Transactional(readOnly = true)
    public DashboardAnalyticsResponse analytics() {
        User user = currentUser();
        LocalDate currentMonth = currentMonth();
        LocalDate firstMonth = currentMonth.minusMonths(5);
        OffsetDateTime end = startOf(currentMonth.plusMonths(1));
        List<DashboardAnalyticsResponse.MonthlyCashFlow> cashFlowTrend = firstMonth.datesUntil(currentMonth.plusMonths(1), java.time.Period.ofMonths(1))
                .map(month -> {
                    OffsetDateTime monthStart = startOf(month);
                    OffsetDateTime nextMonthStart = startOf(month.plusMonths(1));
                    BigDecimal income = zero(transactions.totalCompletedByUserAndTypeBetween(
                            user.getId(), TransactionType.INCOME, monthStart, nextMonthStart));
                    BigDecimal expenses = zero(transactions.totalCompletedByUserAndTypeBetween(
                            user.getId(), TransactionType.EXPENSE, monthStart, nextMonthStart));
                    return new DashboardAnalyticsResponse.MonthlyCashFlow(month, income, expenses, income.subtract(expenses));
                })
                .toList();
        List<DashboardAnalyticsResponse.CategorySpending> categorySpending = transactions
                .currentMonthCompletedExpenseByCategory(user.getId(), startOf(currentMonth), end)
                .stream()
                .map(row -> new DashboardAnalyticsResponse.CategorySpending(
                        row.getCategoryId(), row.getCategoryName(), zero(row.getAmount())
                ))
                .toList();
        return new DashboardAnalyticsResponse(cashFlowTrend, categorySpending);
    }

    private User currentUser() {
        return users.findByEmailIgnoreCase(SecurityContextHolder.getContext().getAuthentication().getName())
                .orElseThrow();
    }

    private LocalDate currentMonth() {
        return LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1);
    }

    private OffsetDateTime startOf(LocalDate month) {
        return month.atStartOfDay().atOffset(ZoneOffset.UTC);
    }

    private BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

}
