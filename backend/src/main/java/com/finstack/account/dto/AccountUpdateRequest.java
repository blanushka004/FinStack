package com.finstack.account.dto;

import com.finstack.account.AccountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.Locale;

public record AccountUpdateRequest(
        @NotBlank(message = "Account name is required")
        @Size(max = 120, message = "Account name must not exceed 120 characters")
        String name,

        @NotNull(message = "Account type is required")
        AccountType accountType,

        @Size(max = 150, message = "Institution name must not exceed 150 characters")
        String institutionName,

        @NotBlank(message = "Currency is required")
        @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be a 3-letter uppercase code")
        String currency,

        @DecimalMin(value = "0.00", message = "Credit limit must not be negative")
        @Digits(integer = 17, fraction = 2, message = "Credit limit must have at most 17 integer and 2 decimal digits")
        BigDecimal creditLimit
) {
    public AccountUpdateRequest {
        name = name == null ? null : name.trim();
        institutionName = institutionName == null || institutionName.trim().isEmpty() ? null : institutionName.trim();
        currency = currency == null ? null : currency.trim().toUpperCase(Locale.ROOT);
    }
}
