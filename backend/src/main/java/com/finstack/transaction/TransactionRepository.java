package com.finstack.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import com.finstack.transaction.projection.CategorySpendingProjection;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, UUID>, JpaSpecificationExecutor<Transaction> {

    Optional<Transaction> findByIdAndAccountUserId(UUID transactionId, UUID userId);

    @Query("select coalesce(sum(t.amount), 0) from Transaction t where t.account.user.id = :userId and t.transactionType = :type and t.status = com.finstack.transaction.TransactionStatus.COMPLETED and t.transactionTime >= :start and t.transactionTime < :end")
    BigDecimal totalCompletedByUserAndTypeBetween(@Param("userId") UUID userId, @Param("type") TransactionType type, @Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select transaction from Transaction transaction where transaction.id = :transactionId and transaction.account.user.id = :userId")
    Optional<Transaction> findByIdAndAccountUserIdForUpdate(
            @Param("transactionId") UUID transactionId,
            @Param("userId") UUID userId
    );

    @Query("select coalesce(sum(t.amount), 0) from Transaction t where t.account.user.id = :userId and t.transactionType = com.finstack.transaction.TransactionType.EXPENSE and t.status = com.finstack.transaction.TransactionStatus.COMPLETED and t.transactionTime >= :start and t.transactionTime < :end")
    BigDecimal totalCompletedExpenseForMonth(@Param("userId") UUID userId,@Param("start") OffsetDateTime start,@Param("end") OffsetDateTime end);
    @Query("select coalesce(sum(t.amount), 0) from Transaction t where t.account.user.id = :userId and t.category.id = :categoryId and t.transactionType = com.finstack.transaction.TransactionType.EXPENSE and t.status = com.finstack.transaction.TransactionStatus.COMPLETED and t.transactionTime >= :start and t.transactionTime < :end")
    BigDecimal categoryCompletedExpenseForMonth(@Param("userId") UUID userId,@Param("categoryId") UUID categoryId,@Param("start") OffsetDateTime start,@Param("end") OffsetDateTime end);

    @Query("""
            select c.id as categoryId, coalesce(c.name, 'Uncategorized') as categoryName,
                   coalesce(sum(t.amount), 0) as amount
            from Transaction t left join t.category c
            where t.account.user.id = :userId
              and t.transactionType = com.finstack.transaction.TransactionType.EXPENSE
              and t.status = com.finstack.transaction.TransactionStatus.COMPLETED
              and t.transactionTime >= :start and t.transactionTime < :end
            group by c.id, c.name
            order by sum(t.amount) desc
            """)
    List<CategorySpendingProjection> currentMonthCompletedExpenseByCategory(
            @Param("userId") UUID userId, @Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end);
}
