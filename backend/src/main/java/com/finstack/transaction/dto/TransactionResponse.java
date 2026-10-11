package com.finstack.transaction.dto;

import com.finstack.transaction.PaymentMethod;
import com.finstack.transaction.Transaction;
import com.finstack.transaction.TransactionStatus;
import com.finstack.transaction.TransactionType;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        UUID accountId,
        String accountName,
        UUID categoryId,
        String categoryName,
        TransactionType transactionType,
        BigDecimal amount,
        String merchant,
        String description,
        PaymentMethod paymentMethod,
        OffsetDateTime transactionTime,
        TransactionStatus status,
        String reference,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getAccount().getId(),
                transaction.getAccount().getName(),
                transaction.getCategory() == null ? null : transaction.getCategory().getId(),
                transaction.getCategory() == null ? null : transaction.getCategory().getName(),
                transaction.getTransactionType(),
                transaction.getAmount(),
                transaction.getMerchant(),
                transaction.getDescription(),
                transaction.getPaymentMethod(),
                transaction.getTransactionTime(),
                transaction.getStatus(),
                transaction.getReference(),
                transaction.getCreatedAt(),
                transaction.getUpdatedAt()
        );
    }
}
