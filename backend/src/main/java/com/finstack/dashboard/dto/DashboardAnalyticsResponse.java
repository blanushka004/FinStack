package com.finstack.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record DashboardAnalyticsResponse(
        List<MonthlyCashFlow> cashFlowTrend,
        List<CategorySpending> categorySpending
) {
    public record MonthlyCashFlow(LocalDate month, BigDecimal income, BigDecimal expenses, BigDecimal netCashFlow) {
    }

    public record CategorySpending(UUID categoryId, String categoryName, BigDecimal amount) {
    }
}
