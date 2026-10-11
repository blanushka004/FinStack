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

public record AccountCreateRequest(
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

        @NotNull(message = "Current balance is required")
        @Digits(integer = 17, fraction = 2, message = "Current balance must have at most 17 integer and 2 decimal digits")
        BigDecimal currentBalance,

        @DecimalMin(value = "0.00", message = "Credit limit must not be negative")
        @Digits(integer = 17, fraction = 2, message = "Credit limit must have at most 17 integer and 2 decimal digits")
        BigDecimal creditLimit
) {
    public AccountCreateRequest {
        name = trim(name);
        institutionName = trimToNull(institutionName);
        currency = currency == null || currency.isBlank() ? "INR" : currency.trim().toUpperCase(Locale.ROOT);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String trimToNull(String value) {
        String trimmed = trim(value);
        return trimmed == null || trimmed.isEmpty() ? null : trimmed;
    }
}
