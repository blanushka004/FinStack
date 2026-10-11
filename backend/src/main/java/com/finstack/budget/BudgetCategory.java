package com.finstack.budget;
import com.finstack.category.TransactionCategory;
import jakarta.persistence.*;
import org.hibernate.Hibernate;
import java.math.BigDecimal;
@Entity @Table(name="budget_categories") public class BudgetCategory {
 @EmbeddedId private BudgetCategoryId id = new BudgetCategoryId();
 @ManyToOne(fetch=FetchType.LAZY) @MapsId("budgetId") @JoinColumn(name="budget_id") private Budget budget;
 @ManyToOne(fetch=FetchType.LAZY, optional=false) @MapsId("categoryId") @JoinColumn(name="category_id") private TransactionCategory category;
 @Column(name="category_limit",nullable=false,precision=19,scale=2) private BigDecimal categoryLimit;
 protected BudgetCategory(){} public BudgetCategory(TransactionCategory category, BigDecimal limit){this.category=category;this.categoryLimit=limit;}
 void setBudget(Budget budget){this.budget=budget;}
 public TransactionCategory getCategory(){return category;} public BigDecimal getCategoryLimit(){return categoryLimit;}
 @Override public boolean equals(Object o){if(this==o)return true;if(o==null||Hibernate.getClass(this)!=Hibernate.getClass(o))return false;BudgetCategory that=(BudgetCategory)o;return id!=null&&id.isAssigned()&&id.equals(that.id);}
 @Override public int hashCode(){return Hibernate.getClass(this).hashCode();}
}
