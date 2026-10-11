package com.finstack.budget.dto;
import jakarta.validation.constraints.*; import java.math.BigDecimal; import java.util.UUID;
public record BudgetCategoryRequest(@NotNull UUID categoryId,@NotNull @DecimalMin(value="0.00",inclusive=false) @Digits(integer=17,fraction=2) BigDecimal limit){}
