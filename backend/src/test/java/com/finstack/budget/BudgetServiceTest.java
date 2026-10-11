package com.finstack.budget;

import com.finstack.budget.dto.BudgetCategoryRequest;
import com.finstack.budget.dto.BudgetCreateRequest;
import com.finstack.budget.dto.BudgetProgressResponse;
import com.finstack.category.CategoryType;
import com.finstack.category.TransactionCategory;
import com.finstack.category.TransactionCategoryRepository;
import com.finstack.transaction.TransactionRepository;
import com.finstack.user.User;
import com.finstack.user.UserRepository;
import com.finstack.user.UserStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BudgetServiceTest {

    @Mock private BudgetRepository budgetRepository;
    @Mock private TransactionCategoryRepository categoryRepository;
    @Mock private UserRepository userRepository;
    @Mock private TransactionRepository transactionRepository;

    private BudgetService service;
    private User user;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new BudgetService(budgetRepository, categoryRepository, userRepository, transactionRepository);
        userId = UUID.randomUUID();
        user = new User("Ada", "Lovelace", "ada@example.com", "hash", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", userId);
        when(userRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("ada@example.com", null, List.of())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createsBudgetWithExpenseCategoryAllocationAndDefaultThreshold() {
        TransactionCategory food = category("FOOD", CategoryType.EXPENSE, null);
        when(categoryRepository.findById(food.getId())).thenReturn(Optional.of(food));
        when(budgetRepository.save(any(Budget.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(new BudgetCreateRequest(
                " September Budget ", LocalDate.of(2026, 9, 1), new BigDecimal("30000.00"), null,
                List.of(new BudgetCategoryRequest(food.getId(), new BigDecimal("8000.00")))
        ));

        ArgumentCaptor<Budget> captor = ArgumentCaptor.forClass(Budget.class);
        verify(budgetRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("September Budget");
        assertThat(captor.getValue().getWarningThresholdPercent()).isEqualTo(80);
        assertThat(captor.getValue().getCategories()).hasSize(1);
    }

    @Test
    void rejectsCategoryAllocationsAboveTheTotalLimit() {
        assertThatThrownBy(() -> service.create(new BudgetCreateRequest(
                "Budget", LocalDate.of(2026, 9, 1), new BigDecimal("100.00"), 80,
                List.of(new BudgetCategoryRequest(UUID.randomUUID(), new BigDecimal("101.00")))
        ))).isInstanceOf(InvalidBudgetException.class);
    }

    @Test
    void rejectsIncomeAndAnotherUsersCustomCategories() {
        TransactionCategory income = category("SALARY", CategoryType.INCOME, null);
        when(categoryRepository.findById(income.getId())).thenReturn(Optional.of(income));
        assertThatThrownBy(() -> service.create(request(income.getId())))
                .isInstanceOf(InvalidBudgetException.class);

        User other = new User("Grace", "Hopper", "grace@example.com", "hash", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(other, "id", UUID.randomUUID());
        TransactionCategory foreignExpense = category("PRIVATE", CategoryType.EXPENSE, other);
        when(categoryRepository.findById(foreignExpense.getId())).thenReturn(Optional.of(foreignExpense));
        assertThatThrownBy(() -> service.create(request(foreignExpense.getId())))
                .isInstanceOf(InvalidBudgetException.class);
    }

    @Test
    void calculatesProgressFromDatabaseAggregates() {
        TransactionCategory food = category("FOOD", CategoryType.EXPENSE, null);
        Budget budget = new Budget(user, "September", LocalDate.of(2026, 9, 1), new BigDecimal("30000.00"), 80);
        ReflectionTestUtils.setField(budget, "id", UUID.randomUUID());
        budget.addCategory(new BudgetCategory(food, new BigDecimal("8000.00")));
        when(budgetRepository.findByIdAndUserId(budget.getId(), userId)).thenReturn(Optional.of(budget));
        when(transactionRepository.totalCompletedExpenseForMonth(any(), any(), any())).thenReturn(new BigDecimal("12000.00"));
        when(transactionRepository.categoryCompletedExpenseForMonth(any(), any(), any(), any())).thenReturn(new BigDecimal("7000.00"));

        BudgetProgressResponse result = service.progress(budget.getId());

        assertThat(result.totalSpent()).isEqualByComparingTo("12000.00");
        assertThat(result.remaining()).isEqualByComparingTo("18000.00");
        assertThat(result.usagePercent()).isEqualByComparingTo("40.00");
        assertThat(result.categories()).singleElement().satisfies(category -> {
            assertThat(category.spent()).isEqualByComparingTo("7000.00");
            assertThat(category.remaining()).isEqualByComparingTo("1000.00");
        });
    }

    @Test
    void doesNotRevealAnotherUsersBudget() {
        UUID budgetId = UUID.randomUUID();
        when(budgetRepository.findByIdAndUserId(budgetId, userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(budgetId)).isInstanceOf(BudgetNotFoundException.class);
    }

    private BudgetCreateRequest request(UUID categoryId) {
        return new BudgetCreateRequest("Budget", LocalDate.of(2026, 9, 1), new BigDecimal("100.00"), 80,
                List.of(new BudgetCategoryRequest(categoryId, new BigDecimal("50.00"))));
    }

    private TransactionCategory category(String name, CategoryType type, User owner) {
        TransactionCategory category = new TransactionCategory(name, type, owner);
        ReflectionTestUtils.setField(category, "id", UUID.randomUUID());
        if (owner == null) {
            ReflectionTestUtils.setField(category, "systemDefined", true);
        }
        return category;
    }
}
