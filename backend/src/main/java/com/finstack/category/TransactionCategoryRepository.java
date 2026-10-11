package com.finstack.category;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TransactionCategoryRepository extends JpaRepository<TransactionCategory, UUID> {

    @Query("""
            select category from TransactionCategory category
            where (category.systemDefined = true or category.user.id = :userId)
              and (:categoryType is null or category.categoryType = :categoryType)
            order by category.name asc
            """)
    List<TransactionCategory> findAvailableForUser(
            @Param("userId") UUID userId,
            @Param("categoryType") CategoryType categoryType
    );

    boolean existsByNameAndCategoryTypeAndUserId(String name, CategoryType categoryType, UUID userId);

    Optional<TransactionCategory> findById(UUID id);
}
