package com.finstack.dashboard.dto;

import java.math.BigDecimal;

public record DashboardSummaryResponse(BigDecimal totalBalance, BigDecimal totalIncome, BigDecimal totalExpenses,
                                       BigDecimal netCashFlow, long activeAccounts, long activeBudgets, long unreadAlerts) {}
