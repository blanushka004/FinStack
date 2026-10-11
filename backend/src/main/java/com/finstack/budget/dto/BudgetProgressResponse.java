package com.finstack.budget.dto;
import java.math.*; import java.util.*;
public record BudgetProgressResponse(BigDecimal totalLimit,BigDecimal totalSpent,BigDecimal remaining,BigDecimal usagePercent,int warningThresholdPercent,boolean exceeded,List<CategoryProgress> categories){public record CategoryProgress(UUID categoryId,String categoryName,BigDecimal limit,BigDecimal spent,BigDecimal remaining,BigDecimal usagePercent,boolean exceeded){}}
