package com.finstack.budget;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
@Embeddable public class BudgetCategoryId implements Serializable { @Column(name="budget_id") private UUID budgetId; @Column(name="category_id") private UUID categoryId; protected BudgetCategoryId(){} public BudgetCategoryId(UUID budgetId, UUID categoryId){this.budgetId=budgetId;this.categoryId=categoryId;} boolean isAssigned(){return budgetId!=null&&categoryId!=null;} @Override public boolean equals(Object o){if(this==o)return true;if(!(o instanceof BudgetCategoryId that))return false;return Objects.equals(budgetId,that.budgetId)&&Objects.equals(categoryId,that.categoryId);}@Override public int hashCode(){return Objects.hash(budgetId,categoryId);} }
