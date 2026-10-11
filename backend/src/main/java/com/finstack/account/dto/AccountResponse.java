package com.finstack.account.dto;

import com.finstack.account.AccountType;
import com.finstack.account.FinancialAccount;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String name,
        AccountType accountType,
        String institutionName,
        String currency,
        BigDecimal currentBalance,
        BigDecimal creditLimit,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static AccountResponse from(FinancialAccount account) {
        return new AccountResponse(
                account.getId(),
                account.getName(),
                account.getAccountType(),
                account.getInstitutionName(),
                account.getCurrency(),
                account.getCurrentBalance(),
                account.getCreditLimit(),
                account.isActive(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
}
