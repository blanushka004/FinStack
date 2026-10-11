package com.finstack.transaction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TransactionCreateIdempotencyRepository extends JpaRepository<TransactionCreateIdempotency, UUID> {
    Optional<TransactionCreateIdempotency> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);
}
