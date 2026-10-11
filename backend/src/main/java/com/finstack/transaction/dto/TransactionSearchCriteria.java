package com.finstack.transaction.dto;

import com.finstack.transaction.TransactionStatus;
import com.finstack.transaction.TransactionType;

import java.time.OffsetDateTime;
import java.util.UUID;

public record TransactionSearchCriteria(
        UUID accountId,
        UUID categoryId,
        TransactionType type,
        TransactionStatus status,
        OffsetDateTime from,
        OffsetDateTime to,
        String search
) {
    public TransactionSearchCriteria {
        search = search == null || search.trim().isEmpty() ? null : search.trim();
    }
}
