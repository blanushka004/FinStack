package com.finstack.account;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.math.BigDecimal;

public interface FinancialAccountRepository extends JpaRepository<FinancialAccount, UUID> {

    @Query("select coalesce(sum(account.currentBalance), 0) from FinancialAccount account where account.user.id = :userId and account.active = true")
    BigDecimal totalActiveBalanceByUserId(@Param("userId") UUID userId);

    long countByUserIdAndActive(UUID userId, boolean active);

    List<FinancialAccount> findAllByUserId(UUID userId);

    List<FinancialAccount> findAllByUserIdAndActive(UUID userId, boolean active);

    Optional<FinancialAccount> findByIdAndUserId(UUID accountId, UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from FinancialAccount account where account.id = :accountId and account.user.id = :userId")
    Optional<FinancialAccount> findByIdAndUserIdForUpdate(
            @Param("accountId") UUID accountId,
            @Param("userId") UUID userId
    );
}
