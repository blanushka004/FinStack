package com.finstack.dashboard;

import com.finstack.account.AccountType;
import com.finstack.account.FinancialAccount;
import com.finstack.category.CategoryType;
import com.finstack.category.TransactionCategory;
import com.finstack.transaction.Transaction;
import com.finstack.transaction.TransactionRepository;
import com.finstack.transaction.TransactionStatus;
import com.finstack.transaction.TransactionType;
import com.finstack.user.User;
import com.finstack.user.UserStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
class DashboardAnalyticsRepositoryTest {
    @Autowired private EntityManager entityManager;
    @Autowired private TransactionRepository transactions;

    @Test
    void aggregatesOnlyOwnersCompletedTransactionsAndMatchingExpenseCategories() {
        User owner = user("analytics-owner@example.com");
        User other = user("analytics-other@example.com");
        FinancialAccount account = account(owner);
        FinancialAccount foreignAccount = account(other);
        TransactionCategory food = category(owner, "FOOD");
        TransactionCategory travel = category(owner, "TRAVEL");
        OffsetDateTime month = OffsetDateTime.now(ZoneOffset.UTC).withDayOfMonth(1).toLocalDate()
                .atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime current = month.plusDays(2);

        transaction(account, food, TransactionType.EXPENSE, "100.00", current, TransactionStatus.COMPLETED);
        transaction(account, travel, TransactionType.EXPENSE, "25.00", current, TransactionStatus.COMPLETED);
        transaction(account, null, TransactionType.EXPENSE, "10.00", current, TransactionStatus.COMPLETED);
        transaction(account, null, TransactionType.INCOME, "200.00", current, TransactionStatus.COMPLETED);
        transaction(account, food, TransactionType.EXPENSE, "999.00", current, TransactionStatus.PENDING);
        transaction(account, food, TransactionType.EXPENSE, "999.00", current, TransactionStatus.CANCELLED);
        transaction(account, food, TransactionType.EXPENSE, "999.00", month.minusDays(1), TransactionStatus.COMPLETED);
        transaction(foreignAccount, null, TransactionType.EXPENSE, "999.00", current, TransactionStatus.COMPLETED);
        entityManager.flush();
        entityManager.clear();

        assertThat(transactions.totalCompletedByUserAndTypeBetween(owner.getId(), TransactionType.INCOME,
                month, month.plusMonths(1))).isEqualByComparingTo("200.00");
        assertThat(transactions.totalCompletedByUserAndTypeBetween(owner.getId(), TransactionType.EXPENSE,
                month, month.plusMonths(1))).isEqualByComparingTo("135.00");
        assertThat(transactions.totalCompletedExpenseForMonth(owner.getId(), month, month.plusMonths(1)))
                .isEqualByComparingTo("135.00");
        assertThat(transactions.categoryCompletedExpenseForMonth(owner.getId(), food.getId(), month, month.plusMonths(1)))
                .isEqualByComparingTo("100.00");
        var spending = transactions.currentMonthCompletedExpenseByCategory(owner.getId(), month, month.plusMonths(1));
        assertThat(spending).hasSize(3);
        var foodSpending = spending.stream().filter(row -> food.getId().equals(row.getCategoryId()))
                .findFirst().orElseThrow();
        assertThat(foodSpending.getCategoryName()).isEqualTo("FOOD");
        assertThat(foodSpending.getAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    void utcMonthBoundariesAreNotShiftedByDatabaseSessionTimezone() {
        User owner = user("utc-boundary-owner@example.com");
        FinancialAccount account = account(owner);
        OffsetDateTime month = OffsetDateTime.parse("2026-10-01T00:00:00Z");
        transaction(account, null, TransactionType.INCOME, "11.00", month, TransactionStatus.COMPLETED);
        transaction(account, null, TransactionType.INCOME, "13.00",
                month.plusMonths(1).minusSeconds(1), TransactionStatus.COMPLETED);
        transaction(account, null, TransactionType.INCOME, "17.00",
                month.plusMonths(1), TransactionStatus.COMPLETED);
        entityManager.flush();
        entityManager.clear();

        assertThat(transactions.totalCompletedByUserAndTypeBetween(owner.getId(), TransactionType.INCOME,
                month, month.plusMonths(1))).isEqualByComparingTo("24.00");
        assertThat(transactions.totalCompletedByUserAndTypeBetween(owner.getId(), TransactionType.INCOME,
                month.plusMonths(1), month.plusMonths(2))).isEqualByComparingTo("17.00");
    }

    private User user(String email) {
        User user = new User("Analytics", "User", email, "hash", UserStatus.ACTIVE);
        entityManager.persist(user);
        return user;
    }

    private FinancialAccount account(User user) {
        FinancialAccount account = new FinancialAccount(user, "Main", AccountType.BANK_ACCOUNT, null,
                "INR", new BigDecimal("2000.00"), null);
        entityManager.persist(account);
        return account;
    }

    private TransactionCategory category(User user, String name) {
        TransactionCategory category = new TransactionCategory(name, CategoryType.EXPENSE, user);
        entityManager.persist(category);
        return category;
    }

    private void transaction(FinancialAccount account, TransactionCategory category, TransactionType type,
                             String amount, OffsetDateTime time, TransactionStatus status) {
        entityManager.persist(new Transaction(account, category, type, new BigDecimal(amount), null, null,
                null, time, status, null));
    }
}
