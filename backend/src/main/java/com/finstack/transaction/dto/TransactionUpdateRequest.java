package com.finstack.transaction.dto;

import com.finstack.transaction.PaymentMethod;
import com.finstack.transaction.TransactionStatus;
import com.finstack.transaction.TransactionType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TransactionUpdateRequest(
        @NotNull(message = "Account id is required")
        UUID accountId,

        UUID categoryId,

        @NotNull(message = "Transaction type is required")
        TransactionType transactionType,

        @NotNull(message = "Amount is required")
        @DecimalMin(value = "0.00", inclusive = false, message = "Amount must be greater than zero")
        @Digits(integer = 17, fraction = 2, message = "Amount must have at most 17 integer and 2 decimal digits")
        BigDecimal amount,

        @Size(max = 180, message = "Merchant must not exceed 180 characters")
        String merchant,

        @Size(max = 500, message = "Description must not exceed 500 characters")
        String description,

        PaymentMethod paymentMethod,

        @NotNull(message = "Transaction time is required")
        OffsetDateTime transactionTime,

        @NotNull(message = "Transaction status is required")
        TransactionStatus status,

        @Size(max = 120, message = "Reference must not exceed 120 characters")
        String reference
) {
    public TransactionUpdateRequest {
        merchant = trimToNull(merchant);
        description = trimToNull(description);
        reference = trimToNull(reference);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
