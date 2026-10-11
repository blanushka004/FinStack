package com.finstack.account;

import com.finstack.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.Hibernate;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "financial_accounts")
public class FinancialAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 40)
    private AccountType accountType;

    @Column(name = "institution_name", length = 150)
    private String institutionName;

    @Column(nullable = false, length = 3)
    private String currency = "INR";

    @Column(name = "current_balance", nullable = false, precision = 19, scale = 2)
    private BigDecimal currentBalance = BigDecimal.ZERO;

    @Column(name = "credit_limit", precision = 19, scale = 2)
    private BigDecimal creditLimit;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected FinancialAccount() {
    }

    public FinancialAccount(
            User user,
            String name,
            AccountType accountType,
            String institutionName,
            String currency,
            BigDecimal currentBalance,
            BigDecimal creditLimit
    ) {
        this.user = user;
        this.name = name;
        this.accountType = accountType;
        this.institutionName = institutionName;
        this.currency = currency;
        this.currentBalance = currentBalance;
        this.creditLimit = creditLimit;
    }

    @PrePersist
    void setCreationTimestamps() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void setUpdatedAt() {
        updatedAt = OffsetDateTime.now(ZoneOffset.UTC);
    }

    public void update(
            String name,
            AccountType accountType,
            String institutionName,
            String currency,
            BigDecimal creditLimit
    ) {
        this.name = name;
        this.accountType = accountType;
        this.institutionName = institutionName;
        this.currency = currency;
        this.creditLimit = creditLimit;
    }

    public void deactivate() {
        active = false;
    }

    public void applyBalanceDelta(BigDecimal delta) {
        currentBalance = currentBalance.add(delta);
    }

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getName() {
        return name;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public String getInstitutionName() {
        return institutionName;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getCurrentBalance() {
        return currentBalance;
    }

    public BigDecimal getCreditLimit() {
        return creditLimit;
    }

    public boolean isActive() {
        return active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || Hibernate.getClass(this) != Hibernate.getClass(other)) {
            return false;
        }
        FinancialAccount account = (FinancialAccount) other;
        return id != null && id.equals(account.id);
    }

    @Override
    public int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
