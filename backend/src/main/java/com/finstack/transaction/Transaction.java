package com.finstack.transaction;

import com.finstack.account.FinancialAccount;
import com.finstack.category.TransactionCategory;
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
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private FinancialAccount account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private TransactionCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 30)
    private TransactionType transactionType;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(length = 180)
    private String merchant;

    @Column(length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 40)
    private PaymentMethod paymentMethod;

    @Column(name = "transaction_time", nullable = false)
    private OffsetDateTime transactionTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TransactionStatus status;

    @Column(length = 120)
    private String reference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Transaction() {
    }

    public Transaction(
            FinancialAccount account,
            TransactionCategory category,
            TransactionType transactionType,
            BigDecimal amount,
            String merchant,
            String description,
            PaymentMethod paymentMethod,
            OffsetDateTime transactionTime,
            TransactionStatus status,
            String reference
    ) {
        this.account = account;
        this.category = category;
        this.transactionType = transactionType;
        this.amount = amount;
        this.merchant = merchant;
        this.description = description;
        this.paymentMethod = paymentMethod;
        this.transactionTime = transactionTime;
        this.status = status;
        this.reference = reference;
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
            FinancialAccount account,
            TransactionCategory category,
            TransactionType transactionType,
            BigDecimal amount,
            String merchant,
            String description,
            PaymentMethod paymentMethod,
            OffsetDateTime transactionTime,
            TransactionStatus status,
            String reference
    ) {
        this.account = account;
        this.category = category;
        this.transactionType = transactionType;
        this.amount = amount;
        this.merchant = merchant;
        this.description = description;
        this.paymentMethod = paymentMethod;
        this.transactionTime = transactionTime;
        this.status = status;
        this.reference = reference;
    }

    public void cancel() {
        status = TransactionStatus.CANCELLED;
    }

    public UUID getId() {
        return id;
    }

    public FinancialAccount getAccount() {
        return account;
    }

    public TransactionCategory getCategory() {
        return category;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getMerchant() {
        return merchant;
    }

    public String getDescription() {
        return description;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public OffsetDateTime getTransactionTime() {
        return transactionTime;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public String getReference() {
        return reference;
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
        Transaction transaction = (Transaction) other;
        return id != null && id.equals(transaction.id);
    }

    @Override
    public int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}
