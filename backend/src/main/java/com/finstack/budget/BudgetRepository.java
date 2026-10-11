package com.finstack.budget;
import org.springframework.data.jpa.repository.*; import java.time.*; import java.util.*;
public interface BudgetRepository extends JpaRepository<Budget,UUID>{
 Optional<Budget> findByIdAndUserId(UUID id,UUID userId); List<Budget> findAllByUserId(UUID userId); List<Budget> findAllByUserIdAndActive(UUID userId,boolean active); List<Budget> findAllByUserIdAndBudgetMonthAndActive(UUID userId,LocalDate month,boolean active);
 long countByUserIdAndActive(UUID userId, boolean active);
}
