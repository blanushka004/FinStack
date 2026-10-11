package com.finstack.category.dto;

import com.finstack.category.CategoryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.Locale;

public record CategoryCreateRequest(
        @NotBlank(message = "Category name is required")
        @Size(max = 100, message = "Category name must not exceed 100 characters")
        String name,

        @NotNull(message = "Category type is required")
        CategoryType categoryType
) {
    public CategoryCreateRequest {
        name = name == null ? null : name.trim().toUpperCase(Locale.ROOT);
    }
}
