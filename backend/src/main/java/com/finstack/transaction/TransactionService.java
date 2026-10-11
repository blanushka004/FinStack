package com.finstack.transaction;

import com.finstack.account.AccountNotFoundException;
import com.finstack.account.AccountType;
import com.finstack.budget.BudgetEvaluationService;
import com.finstack.account.FinancialAccount;
import com.finstack.account.FinancialAccountRepository;
import com.finstack.category.CategoryNotFoundException;
import com.finstack.category.CategoryType;
import com.finstack.category.TransactionCategory;
import com.finstack.category.TransactionCategoryRepository;
import com.finstack.transaction.dto.TransactionCreateRequest;
import com.finstack.transaction.dto.TransactionResponse;
import com.finstack.transaction.dto.TransactionSearchCriteria;
import com.finstack.transaction.dto.TransactionUpdateRequest;
import com.finstack.user.User;
import com.finstack.user.UserRepository;
import com.finstack.event.TransactionEventPublisher;
import com.finstack.event.TransactionEventType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class TransactionService {

    private static final int MAX_PAGE_SIZE = 100;

    private final TransactionRepository transactionRepository;
    private final FinancialAccountRepository accountRepository;
    private final TransactionCategoryRepository categoryRepository;
    private final UserRepository userRepository;
    private final BudgetEvaluationService budgetEvaluationService;
    private final TransactionEventPublisher transactionEventPublisher;

    public TransactionService(
            TransactionRepository transactionRepository,
            FinancialAccountRepository accountRepository,
            TransactionCategoryRepository categoryRepository,
            UserRepository userRepository,
            BudgetEvaluationService budgetEvaluationService,
            TransactionEventPublisher transactionEventPublisher
    ) {
        this.transactionRepository = transactionRepository;
        this.accountRepository = accountRepository;
        this.categoryRepository = categoryRepository;
        this.userRepository = userRepository;
        this.budgetEvaluationService = budgetEvaluationService;
        this.transactionEventPublisher = transactionEventPublisher;
    }

    @Transactional
    public TransactionResponse create(TransactionCreateRequest request) {
        validateAmount(request.amount());
        User user = getAuthenticatedUser();
        FinancialAccount account = lockOwnedAccount(user, request.accountId());
        ensureAccountIsActive(account);
        TransactionCategory category = resolveCategory(user, request.categoryId(), request.transactionType());

        Transaction transaction = new Transaction(
                account,
                category,
                request.transactionType(),
                request.amount(),
                request.merchant(),
                request.description(),
                request.paymentMethod(),
                request.transactionTime(),
                request.status(),
                request.reference()
        );
        applyFinancialEffect(account, transaction.getTransactionType(), transaction.getAmount(), transaction.getStatus());

        Transaction savedTransaction = transactionRepository.save(transaction);
        evaluateBudgetsForCompletedExpense(user, savedTransaction);
        transactionEventPublisher.afterCommit(savedTransaction, TransactionEventType.TRANSACTION_CREATED);
        return TransactionResponse.from(savedTransaction);
    }

    @Transactional(readOnly = true)
    public TransactionResponse getById(UUID transactionId) {
        User user = getAuthenticatedUser();
        return TransactionResponse.from(findOwnedTransaction(transactionId, user));
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> search(TransactionSearchCriteria criteria, Pageable pageable) {
        validateSearchCriteria(criteria);
        User user = getAuthenticatedUser();
        Specification<Transaction> specification = ownedBy(user.getId());

        if (criteria.accountId() != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("account").get("id"), criteria.accountId())
            );
        }
        if (criteria.categoryId() != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("category").get("id"), criteria.categoryId())
            );
        }
        if (criteria.type() != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("transactionType"), criteria.type())
            );
        }
        if (criteria.status() != null) {
            specification = specification.and((root, query, builder) ->
                    builder.equal(root.get("status"), criteria.status())
            );
        }
        if (criteria.from() != null) {
            specification = specification.and((root, query, builder) ->
                    builder.greaterThanOrEqualTo(root.get("transactionTime"), criteria.from())
            );
        }
        if (criteria.to() != null) {
            specification = specification.and((root, query, builder) ->
                    builder.lessThanOrEqualTo(root.get("transactionTime"), criteria.to())
            );
        }
        if (criteria.search() != null) {
            String pattern = "%" + criteria.search().toLowerCase(Locale.ROOT) + "%";
            specification = specification.and((root, query, builder) -> builder.or(
                    builder.like(builder.lower(root.get("merchant")), pattern),
                    builder.like(builder.lower(root.get("description")), pattern)
            ));
        }

        Pageable cappedPageable = PageRequest.of(
                pageable.getPageNumber(),
                Math.min(MAX_PAGE_SIZE, Math.max(1, pageable.getPageSize())),
                pageable.getSort()
        );
        return transactionRepository.findAll(specification, cappedPageable).map(TransactionResponse::from);
    }

    @Transactional
    public TransactionResponse update(UUID transactionId, TransactionUpdateRequest request) {
        validateAmount(request.amount());
        User user = getAuthenticatedUser();
        Transaction transaction = lockOwnedTransaction(transactionId, user);
        OffsetDateTime originalTransactionTime = transaction.getTransactionTime();
        TransactionType originalTransactionType = transaction.getTransactionType();
        TransactionStatus originalStatus = transaction.getStatus();
        UUID originalAccountId = transaction.getAccount().getId();
        Map<UUID, FinancialAccount> lockedAccounts = lockOwnedAccounts(
                user, originalAccountId, request.accountId()
        );
        FinancialAccount originalAccount = lockedAccounts.get(originalAccountId);
        FinancialAccount updatedAccount = lockedAccounts.get(request.accountId());
        ensureAccountIsActive(updatedAccount);
        TransactionCategory category = resolveCategory(user, request.categoryId(), request.transactionType());

        reverseFinancialEffect(originalAccount, transaction);
        transaction.update(
                updatedAccount,
                category,
                request.transactionType(),
                request.amount(),
                request.merchant(),
                request.description(),
                request.paymentMethod(),
                request.transactionTime(),
                request.status(),
                request.reference()
        );
        applyFinancialEffect(updatedAccount, transaction.getTransactionType(), transaction.getAmount(), transaction.getStatus());

        Transaction savedTransaction = transactionRepository.save(transaction);
        evaluateBudgetsForCompletedExpense(user, originalTransactionType, originalStatus, originalTransactionTime);
        evaluateBudgetsForCompletedExpense(user, savedTransaction);
        transactionEventPublisher.afterCommit(savedTransaction, TransactionEventType.TRANSACTION_UPDATED);
        return TransactionResponse.from(savedTransaction);
    }

    @Transactional
    public void cancel(UUID transactionId) {
        User user = getAuthenticatedUser();
        Transaction transaction = lockOwnedTransaction(transactionId, user);
        FinancialAccount account = lockOwnedAccount(user, transaction.getAccount().getId());

        if (transaction.getStatus() != TransactionStatus.CANCELLED) {
            OffsetDateTime transactionTime = transaction.getTransactionTime();
            TransactionType transactionType = transaction.getTransactionType();
            TransactionStatus transactionStatus = transaction.getStatus();
            reverseFinancialEffect(account, transaction);
            transaction.cancel();
            transactionRepository.save(transaction);
            evaluateBudgetsForCompletedExpense(user, transactionType, transactionStatus, transactionTime);
            transactionEventPublisher.afterCommit(transaction, TransactionEventType.TRANSACTION_CANCELLED);
        }
    }

    private Transaction findOwnedTransaction(UUID transactionId, User user) {
        return transactionRepository.findByIdAndAccountUserId(transactionId, user.getId())
                .orElseThrow(TransactionNotFoundException::new);
    }

    private Transaction lockOwnedTransaction(UUID transactionId, User user) {
        return transactionRepository.findByIdAndAccountUserIdForUpdate(transactionId, user.getId())
                .orElseThrow(TransactionNotFoundException::new);
    }

    private FinancialAccount lockOwnedAccount(User user, UUID accountId) {
        return accountRepository.findByIdAndUserIdForUpdate(accountId, user.getId())
                .orElseThrow(AccountNotFoundException::new);
    }

    private Map<UUID, FinancialAccount> lockOwnedAccounts(User user, UUID... accountIds) {
        Map<UUID, FinancialAccount> accounts = new LinkedHashMap<>();
        Arrays.stream(accountIds)
                .distinct()
                .sorted(Comparator.comparing(UUID::toString))
                .forEach(accountId -> accounts.put(accountId, lockOwnedAccount(user, accountId)));
        return accounts;
    }

    private TransactionCategory resolveCategory(User user, UUID categoryId, TransactionType transactionType) {
        if (categoryId == null) {
            return null;
        }

        TransactionCategory category = categoryRepository.findById(categoryId)
                .orElseThrow(CategoryNotFoundException::new);
        if (!category.isSystemDefined()
                && (category.getUser() == null || !user.getId().equals(category.getUser().getId()))) {
            throw new CategoryNotFoundException();
        }
        if (category.getCategoryType() != CategoryType.valueOf(transactionType.name())) {
            throw new CategoryTypeMismatchException();
        }
        return category;
    }

    private void ensureAccountIsActive(FinancialAccount account) {
        if (!account.isActive()) {
            throw new InactiveAccountException();
        }
    }

    private void validateAmount(BigDecimal amount) {
        if (amount == null || amount.signum() <= 0) {
            throw new InvalidTransactionAmountException();
        }
    }

    private void validateSearchCriteria(TransactionSearchCriteria criteria) {
        OffsetDateTime from = criteria.from();
        OffsetDateTime to = criteria.to();
        if (from != null && to != null && from.isAfter(to)) {
            throw new InvalidTransactionFilterException();
        }
    }

    private Specification<Transaction> ownedBy(UUID userId) {
        return (root, query, builder) -> builder.equal(
                root.join("account").join("user").get("id"), userId
        );
    }

    private void applyFinancialEffect(
            FinancialAccount account,
            TransactionType transactionType,
            BigDecimal amount,
            TransactionStatus status
    ) {
        if (status == TransactionStatus.COMPLETED) {
            applyBalanceDelta(account, financialEffect(transactionType, amount));
        }
    }

    private void reverseFinancialEffect(FinancialAccount account, Transaction transaction) {
        if (transaction.getStatus() == TransactionStatus.COMPLETED) {
            applyBalanceDelta(account, financialEffect(transaction.getTransactionType(), transaction.getAmount()).negate());
        }
    }

    private void applyBalanceDelta(FinancialAccount account, BigDecimal delta) {
        BigDecimal resultingBalance = account.getCurrentBalance().add(delta);
        if (account.getAccountType() == AccountType.CREDIT_CARD) {
            BigDecimal creditLimit = account.getCreditLimit();
            if (creditLimit != null
                    && resultingBalance.signum() < 0
                    && resultingBalance.abs().compareTo(creditLimit) > 0) {
                throw new InsufficientFundsException("Credit limit would be exceeded");
            }
        } else if (resultingBalance.signum() < 0) {
            throw new InsufficientFundsException("Insufficient funds for this expense");
        }
        account.applyBalanceDelta(delta);
    }

    private BigDecimal financialEffect(TransactionType transactionType, BigDecimal amount) {
        return transactionType == TransactionType.INCOME ? amount : amount.negate();
    }

    private void evaluateBudgetsForCompletedExpense(User user, Transaction transaction) {
        evaluateBudgetsForCompletedExpense(user, transaction.getTransactionType(), transaction.getStatus(), transaction.getTransactionTime());
    }

    private void evaluateBudgetsForCompletedExpense(
            User user,
            TransactionType transactionType,
            TransactionStatus status,
            OffsetDateTime transactionTime
    ) {
        if (transactionType == TransactionType.EXPENSE && status == TransactionStatus.COMPLETED) {
            budgetEvaluationService.evaluate(user, transactionTime);
        }
    }

    private User getAuthenticatedUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required");
        }

        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Authenticated user is unavailable"));
    }
}
