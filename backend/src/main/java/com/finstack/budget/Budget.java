package com.finstack.budget;

import com.finstack.user.User;
import jakarta.persistence.*;
import org.hibernate.Hibernate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Entity @Table(name = "budgets")
public class Budget {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(nullable = false, length = 120) private String name;
    @Column(name = "budget_month", nullable = false) private LocalDate budgetMonth;
    @Column(name = "total_limit", nullable = false, precision = 19, scale = 2) private BigDecimal totalLimit;
    @Column(name = "warning_threshold_percent", nullable = false) private int warningThresholdPercent = 80;
    @Column(nullable = false) private boolean active = true;
    @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;
    @Column(name = "updated_at", nullable = false) private OffsetDateTime updatedAt;
    @OneToMany(mappedBy = "budget", cascade = CascadeType.ALL, orphanRemoval = true) private Set<BudgetCategory> categories = new HashSet<>();
    protected Budget() {}
    public Budget(User user, String name, LocalDate budgetMonth, BigDecimal totalLimit, int warningThresholdPercent) { this.user=user; this.name=name; this.budgetMonth=budgetMonth; this.totalLimit=totalLimit; this.warningThresholdPercent=warningThresholdPercent; }
    @PrePersist void created(){ OffsetDateTime now=OffsetDateTime.now(ZoneOffset.UTC); if(createdAt==null)createdAt=now; if(updatedAt==null)updatedAt=now; }
    @PreUpdate void updated(){ updatedAt=OffsetDateTime.now(ZoneOffset.UTC); }
    public void update(String name, LocalDate month, BigDecimal limit, int threshold, Set<BudgetCategory> allocations){ this.name=name; budgetMonth=month; totalLimit=limit; warningThresholdPercent=threshold; categories.clear(); allocations.forEach(this::addCategory); }
    public void addCategory(BudgetCategory allocation){ allocation.setBudget(this); categories.add(allocation); }
    public void deactivate(){active=false;}
    public UUID getId(){return id;} public User getUser(){return user;} public String getName(){return name;} public LocalDate getBudgetMonth(){return budgetMonth;} public BigDecimal getTotalLimit(){return totalLimit;} public int getWarningThresholdPercent(){return warningThresholdPercent;} public boolean isActive(){return active;} public OffsetDateTime getCreatedAt(){return createdAt;} public OffsetDateTime getUpdatedAt(){return updatedAt;} public Set<BudgetCategory> getCategories(){return Set.copyOf(categories);}
    @Override public boolean equals(Object o){ if(this==o)return true; if(o==null||Hibernate.getClass(this)!=Hibernate.getClass(o))return false; Budget b=(Budget)o; return id!=null&&id.equals(b.id); }
    @Override public int hashCode(){return Hibernate.getClass(this).hashCode();}
}
