package com.finstack.category.dto;

import com.finstack.category.CategoryType;
import com.finstack.category.TransactionCategory;

import java.time.OffsetDateTime;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        String name,
        CategoryType categoryType,
        boolean systemDefined,
        OffsetDateTime createdAt
) {
    public static CategoryResponse from(TransactionCategory category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getCategoryType(),
                category.isSystemDefined(),
                category.getCreatedAt()
        );
    }
}
