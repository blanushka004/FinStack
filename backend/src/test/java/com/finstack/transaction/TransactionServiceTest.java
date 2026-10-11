package com.finstack.transaction;

import com.finstack.account.FinancialAccount;
import com.finstack.account.FinancialAccountRepository;
import com.finstack.account.AccountType;
import com.finstack.budget.BudgetEvaluationService;
import com.finstack.category.CategoryType;
import com.finstack.category.TransactionCategory;
import com.finstack.category.TransactionCategoryRepository;
import com.finstack.transaction.dto.TransactionCreateRequest;
import com.finstack.transaction.dto.TransactionSearchCriteria;
import com.finstack.transaction.dto.TransactionUpdateRequest;
import com.finstack.user.User;
import com.finstack.user.UserRepository;
import com.finstack.user.UserStatus;
import com.finstack.event.TransactionEventPublisher;
import com.finstack.event.TransactionEventType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private FinancialAccountRepository accountRepository;

    @Mock
    private TransactionCategoryRepository categoryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BudgetEvaluationService budgetEvaluationService;

    @Mock
    private TransactionEventPublisher transactionEventPublisher;

    private TransactionService transactionService;
    private User currentUser;
    private UUID currentUserId;
    private FinancialAccount primaryAccount;

    @BeforeEach
    void setUp() {
        transactionService = new TransactionService(
                transactionRepository, accountRepository, categoryRepository, userRepository, budgetEvaluationService, transactionEventPublisher
        );
        currentUserId = UUID.randomUUID();
        currentUser = new User("Ada", "Lovelace", "ada@example.com", "password-hash", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(currentUser, "id", currentUserId);
        primaryAccount = account("Primary", "1000.00");
        lenient().when(userRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(currentUser));
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated("ada@example.com", null, List.of())
        );
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void completedIncomeIncreasesAccountBalance() {
        stubLocked(primaryAccount);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        transactionService.create(createRequest(
                primaryAccount.getId(), TransactionType.INCOME, "250.00", TransactionStatus.COMPLETED, null
        ));

        assertThat(primaryAccount.getCurrentBalance()).isEqualByComparingTo("1250.00");
        verify(budgetEvaluationService, never()).evaluate(any(User.class), any(OffsetDateTime.class));
    }

    @Test
    void completedExpenseDecreasesAccountBalance() {
        stubLocked(primaryAccount);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        transactionService.create(createRequest(
                primaryAccount.getId(), TransactionType.EXPENSE, "250.00", TransactionStatus.COMPLETED, null
        ));

        assertThat(primaryAccount.getCurrentBalance()).isEqualByComparingTo("750.00");
        verify(transactionEventPublisher).afterCommit(any(Transaction.class), eq(TransactionEventType.TRANSACTION_CREATED));
    }

    @Test
    void rejectsSavingsExpenseWhenItExceedsAvailableBalanceWithoutSavingOrPublishing() {
        FinancialAccount savings = account("Savings", "1000.00", AccountType.SAVINGS_ACCOUNT, null);
        stubLocked(savings);

        assertThatThrownBy(() -> transactionService.create(createRequest(
                savings.getId(), TransactionType.EXPENSE, "1500.00", TransactionStatus.COMPLETED, null
        ))).isInstanceOf(InsufficientFundsException.class)
                .hasMessage("Insufficient funds for this expense");

        assertThat(savings.getCurrentBalance()).isEqualByComparingTo("1000.00");
        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(transactionEventPublisher, never()).afterCommit(any(Transaction.class), any(TransactionEventType.class));
    }

    @Test
    void acceptsSavingsExpenseWhenFundsAreAvailable() {
        FinancialAccount savings = account("Savings", "1000.00", AccountType.SAVINGS_ACCOUNT, null);
        stubLocked(savings);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        transactionService.create(createRequest(savings.getId(), TransactionType.EXPENSE, "600.00", TransactionStatus.COMPLETED, null));

        assertThat(savings.getCurrentBalance()).isEqualByComparingTo("400.00");
    }

    @Test
    void pendingTransactionDoesNotChangeAccountBalance() {
        stubLocked(primaryAccount);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        transactionService.create(createRequest(
                primaryAccount.getId(), TransactionType.EXPENSE, "250.00", TransactionStatus.PENDING, null
        ));

        assertThat(primaryAccount.getCurrentBalance()).isEqualByComparingTo("1000.00");
    }

    @Test
    void cancelledTransactionCreatedDirectlyDoesNotChangeAccountBalance() {
        stubLocked(primaryAccount);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        transactionService.create(createRequest(
                primaryAccount.getId(), TransactionType.EXPENSE, "250.00", TransactionStatus.CANCELLED, null
        ));

        assertThat(primaryAccount.getCurrentBalance()).isEqualByComparingTo("1000.00");
    }

    @Test
    void creditCardMayUseConfiguredCreditButCannotExceedItsLimit() {
        FinancialAccount card = account("Card", "0.00", AccountType.CREDIT_CARD, new BigDecimal("1000.00"));
        stubLocked(card);
        when(transactionRepository.save(any(Transaction.class))).thenAnswer(invocation -> invocation.getArgument(0));

        transactionService.create(createRequest(card.getId(), TransactionType.EXPENSE, "900.00", TransactionStatus.COMPLETED, null));
        assertThat(card.getCurrentBalance()).isEqualByComparingTo("-900.00");

        assertThatThrownBy(() -> transactionService.create(createRequest(
                card.getId(), TransactionType.EXPENSE, "200.00", TransactionStatus.COMPLETED, null
        ))).isInstanceOf(InsufficientFundsException.class)
                .hasMessage("Credit limit would be exceeded");
        assertThat(card.getCurrentBalance()).isEqualByComparingTo("-900.00");
    }

    @Test
    void rejectsNonPositiveTransactionAmounts() {
        assertThatThrownBy(() -> transactionService.create(createRequest(
                primaryAccount.getId(), TransactionType.EXPENSE, "0.00", TransactionStatus.COMPLETED, null
        ))).isInstanceOf(InvalidTransactionAmountException.class);

        verify(accountRepository, never()).findByIdAndUserIdForUpdate(any(), any());
        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void rejectsCreatingTransactionsAgainstAnotherUsersAccount() {
        UUID foreignAccountId = UUID.randomUUID();
        when(accountRepository.findByIdAndUserIdForUpdate(foreignAccountId, currentUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.create(createRequest(
                foreignAccountId, TransactionType.EXPENSE, "10.00", TransactionStatus.COMPLETED, null
        ))).isInstanceOf(com.finstack.account.AccountNotFoundException.class);

        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void cancellingCompletedTransactionReversesItsBalanceEffect() {
        primaryAccount = account("Primary", "900.00");
        UUID transactionId = UUID.randomUUID();
        Transaction transaction = transaction(primaryAccount, TransactionType.EXPENSE, "100.00", TransactionStatus.COMPLETED);
        stubOwnedTransaction(transactionId, transaction);
        stubLocked(primaryAccount);

        transactionService.cancel(transactionId);

        assertThat(primaryAccount.getCurrentBalance()).isEqualByComparingTo("1000.00");
        assertThat(transaction.getStatus()).isEqualTo(TransactionStatus.CANCELLED);
        verify(transactionRepository).save(transaction);
        verify(budgetEvaluationService).evaluate(eq(currentUser), any(OffsetDateTime.class));
    }

    @Test
    void cancellingAnAlreadyCancelledTransactionDoesNotReverseItsBalanceAgain() {
        UUID transactionId = UUID.randomUUID();
        Transaction transaction = transaction(primaryAccount, TransactionType.EXPENSE, "100.00", TransactionStatus.CANCELLED);
        stubOwnedTransaction(transactionId, transaction);
        stubLocked(primaryAccount);

        transactionService.cancel(transactionId);

        assertThat(primaryAccount.getCurrentBalance()).isEqualByComparingTo("1000.00");
        verify(transactionRepository, never()).save(transaction);
    }

    @Test
    void updatingCompletedAmountReversesOldEffectBeforeApplyingNewEffect() {
        primaryAccount = account("Primary", "9000.00");
        UUID transactionId = UUID.randomUUID();
        Transaction transaction = transaction(primaryAccount, TransactionType.EXPENSE, "1000.00", TransactionStatus.COMPLETED);
        stubOwnedTransaction(transactionId, transaction);
        stubLocked(primaryAccount);
        when(transactionRepository.save(transaction)).thenReturn(transaction);

        transactionService.update(transactionId, updateRequest(
                primaryAccount.getId(), TransactionType.EXPENSE, "600.00", TransactionStatus.COMPLETED, null
        ));

        assertThat(primaryAccount.getCurrentBalance()).isEqualByComparingTo("9400.00");
        verify(budgetEvaluationService, times(2)).evaluate(eq(currentUser), any(OffsetDateTime.class));
    }

    @Test
    void updatingIncomeToExpenseReversesAndAppliesTheCorrectEffects() {
        primaryAccount = account("Primary", "1500.00");
        UUID transactionId = UUID.randomUUID();
        Transaction transaction = transaction(primaryAccount, TransactionType.INCOME, "500.00", TransactionStatus.COMPLETED);
        stubOwnedTransaction(transactionId, transaction);
        stubLocked(primaryAccount);
        when(transactionRepository.save(transaction)).thenReturn(transaction);

        transactionService.update(transactionId, updateRequest(
                primaryAccount.getId(), TransactionType.EXPENSE, "300.00", TransactionStatus.COMPLETED, null
        ));

        assertThat(primaryAccount.getCurrentBalance()).isEqualByComparingTo("700.00");
    }

    @Test
    void movingTransactionToAnotherAccountReversesAndAppliesBalances() {
        primaryAccount = account("Primary", "900.00");
        FinancialAccount secondaryAccount = account("Secondary", "200.00");
        UUID transactionId = UUID.randomUUID();
        Transaction transaction = transaction(primaryAccount, TransactionType.EXPENSE, "100.00", TransactionStatus.COMPLETED);
        stubOwnedTransaction(transactionId, transaction);
        stubLocked(primaryAccount);
        stubLocked(secondaryAccount);
        when(transactionRepository.save(transaction)).thenReturn(transaction);

        transactionService.update(transactionId, updateRequest(
                secondaryAccount.getId(), TransactionType.EXPENSE, "50.00", TransactionStatus.COMPLETED, null
        ));

        assertThat(primaryAccount.getCurrentBalance()).isEqualByComparingTo("1000.00");
        assertThat(secondaryAccount.getCurrentBalance()).isEqualByComparingTo("150.00");
    }

    @Test
    void rejectsCategoryWhoseTypeDoesNotMatchTransactionType() {
        UUID categoryId = UUID.randomUUID();
        TransactionCategory incomeCategory = new TransactionCategory("SALARY", CategoryType.INCOME, currentUser);
        ReflectionTestUtils.setField(incomeCategory, "id", categoryId);
        stubLocked(primaryAccount);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(incomeCategory));

        assertThatThrownBy(() -> transactionService.create(createRequest(
                primaryAccount.getId(), TransactionType.EXPENSE, "10.00", TransactionStatus.COMPLETED, categoryId
        ))).isInstanceOf(CategoryTypeMismatchException.class);

        verify(transactionRepository, never()).save(any(Transaction.class));
    }

    @Test
    void rejectsNewTransactionsForInactiveAccounts() {
        ReflectionTestUtils.setField(primaryAccount, "active", false);
        stubLocked(primaryAccount);

        assertThatThrownBy(() -> transactionService.create(createRequest(
                primaryAccount.getId(), TransactionType.EXPENSE, "10.00", TransactionStatus.COMPLETED, null
        ))).isInstanceOf(InactiveAccountException.class);
    }

    @Test
    void doesNotAllowUsingAnotherUsersCustomCategory() {
        User anotherUser = new User("Grace", "Hopper", "grace@example.com", "password-hash", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(anotherUser, "id", UUID.randomUUID());
        UUID categoryId = UUID.randomUUID();
        TransactionCategory foreignCategory = new TransactionCategory("PRIVATE", CategoryType.EXPENSE, anotherUser);
        ReflectionTestUtils.setField(foreignCategory, "id", categoryId);
        stubLocked(primaryAccount);
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(foreignCategory));

        assertThatThrownBy(() -> transactionService.create(createRequest(
                primaryAccount.getId(), TransactionType.EXPENSE, "10.00", TransactionStatus.COMPLETED, categoryId
        ))).isInstanceOf(com.finstack.category.CategoryNotFoundException.class);
    }

    @Test
    void doesNotRetrieveAnotherUsersTransaction() {
        UUID foreignTransactionId = UUID.randomUUID();
        when(transactionRepository.findByIdAndAccountUserId(foreignTransactionId, currentUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.getById(foreignTransactionId))
                .isInstanceOf(TransactionNotFoundException.class);
    }

    @Test
    void doesNotUpdateAnotherUsersTransaction() {
        UUID foreignTransactionId = UUID.randomUUID();
        when(transactionRepository.findByIdAndAccountUserIdForUpdate(foreignTransactionId, currentUserId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.update(foreignTransactionId, updateRequest(
                primaryAccount.getId(), TransactionType.EXPENSE, "10.00", TransactionStatus.COMPLETED, null
        ))).isInstanceOf(TransactionNotFoundException.class);

        verify(accountRepository, never()).findByIdAndUserIdForUpdate(any(), any());
    }

    @Test
    void doesNotCancelAnotherUsersTransaction() {
        UUID foreignTransactionId = UUID.randomUUID();
        when(transactionRepository.findByIdAndAccountUserIdForUpdate(foreignTransactionId, currentUserId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.cancel(foreignTransactionId))
                .isInstanceOf(TransactionNotFoundException.class);

        verify(accountRepository, never()).findByIdAndUserIdForUpdate(any(), any());
    }

    @Test
    void delegatesFiltersToDatabaseAndCapsPageSize() {
        Transaction transaction = transaction(primaryAccount, TransactionType.INCOME, "100.00", TransactionStatus.COMPLETED);
        when(transactionRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(transaction)));
        TransactionSearchCriteria criteria = new TransactionSearchCriteria(
                primaryAccount.getId(), null, TransactionType.INCOME, TransactionStatus.COMPLETED,
                OffsetDateTime.parse("2026-01-01T00:00:00Z"),
                OffsetDateTime.parse("2026-12-31T23:59:59Z"), "salary"
        );

        Page<?> result = transactionService.search(criteria, PageRequest.of(0, 250, Sort.by("transactionTime").descending()));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(transactionRepository).findAll(any(Specification.class), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
        assertThat(result.getContent()).hasSize(1);
    }

    private FinancialAccount account(String name, String balance) {
        return account(name, balance, AccountType.BANK_ACCOUNT, null);
    }

    private FinancialAccount account(String name, String balance, AccountType accountType, BigDecimal creditLimit) {
        FinancialAccount account = new FinancialAccount(
                currentUser, name, accountType,
                null, "INR", new BigDecimal(balance), creditLimit
        );
        ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
        return account;
    }

    private Transaction transaction(
            FinancialAccount account,
            TransactionType type,
            String amount,
            TransactionStatus status
    ) {
        return new Transaction(
                account, null, type, new BigDecimal(amount), "Merchant", null, null,
                OffsetDateTime.now(), status, null
        );
    }

    private TransactionCreateRequest createRequest(
            UUID accountId,
            TransactionType type,
            String amount,
            TransactionStatus status,
            UUID categoryId
    ) {
        return new TransactionCreateRequest(
                accountId, categoryId, type, new BigDecimal(amount), " Merchant ", null,
                PaymentMethod.UPI, OffsetDateTime.now(), status, null
        );
    }

    private TransactionUpdateRequest updateRequest(
            UUID accountId,
            TransactionType type,
            String amount,
            TransactionStatus status,
            UUID categoryId
    ) {
        return new TransactionUpdateRequest(
                accountId, categoryId, type, new BigDecimal(amount), "Merchant", null,
                PaymentMethod.UPI, OffsetDateTime.now(), status, null
        );
    }

    private void stubLocked(FinancialAccount account) {
        when(accountRepository.findByIdAndUserIdForUpdate(account.getId(), currentUserId))
                .thenReturn(Optional.of(account));
    }

    private void stubOwnedTransaction(UUID transactionId, Transaction transaction) {
        when(transactionRepository.findByIdAndAccountUserIdForUpdate(transactionId, currentUserId))
                .thenReturn(Optional.of(transaction));
    }
}
