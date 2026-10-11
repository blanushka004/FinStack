package com.finstack.alert;
import org.springframework.data.domain.*; import org.springframework.data.jpa.repository.*; import org.springframework.data.repository.query.Param; import java.util.*;
public interface AlertRepository extends JpaRepository<Alert,UUID>,JpaSpecificationExecutor<Alert>{
 boolean existsByRelatedBudgetIdAndAlertType(UUID budgetId,AlertType type); Optional<Alert> findByIdAndUserId(UUID id,UUID userId);
 boolean existsByRelatedBudgetIdAndRelatedCategoryIdAndAlertType(UUID budgetId,UUID categoryId,AlertType type);
 boolean existsByRelatedTransactionIdAndAlertType(UUID transactionId, AlertType type);
 long countByUserIdAndRead(UUID userId, boolean read);
 @Modifying @Query("update Alert a set a.read = true where a.user.id = :userId and a.read = false") int markAllReadByUserId(@Param("userId") UUID userId);
}
