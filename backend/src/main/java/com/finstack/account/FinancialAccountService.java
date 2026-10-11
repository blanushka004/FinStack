package com.finstack.account;

import com.finstack.account.dto.AccountCreateRequest;
import com.finstack.account.dto.AccountResponse;
import com.finstack.account.dto.AccountUpdateRequest;
import com.finstack.user.User;
import com.finstack.user.UserRepository;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class FinancialAccountService {

    private final FinancialAccountRepository accountRepository;
    private final UserRepository userRepository;

    public FinancialAccountService(
            FinancialAccountRepository accountRepository,
            UserRepository userRepository
    ) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public AccountResponse create(AccountCreateRequest request) {
        validateCreditLimit(request.accountType(), request.creditLimit());
        validateBalancePolicy(request.accountType(), request.currentBalance(), request.creditLimit());
        User user = getAuthenticatedUser();
        FinancialAccount account = new FinancialAccount(
                user,
                request.name(),
                request.accountType(),
                request.institutionName(),
                request.currency(),
                request.currentBalance(),
                request.creditLimit()
        );

        return AccountResponse.from(accountRepository.save(account));
    }

    @Transactional(readOnly = true)
    public List<AccountResponse> list(Boolean active) {
        User user = getAuthenticatedUser();
        List<FinancialAccount> accounts = active == null
                ? accountRepository.findAllByUserId(user.getId())
                : accountRepository.findAllByUserIdAndActive(user.getId(), active);

        return accounts.stream().map(AccountResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse getById(UUID accountId) {
        return AccountResponse.from(findOwnedAccount(accountId));
    }

    @Transactional
    public AccountResponse update(UUID accountId, AccountUpdateRequest request) {
        validateCreditLimit(request.accountType(), request.creditLimit());
        FinancialAccount account = lockOwnedAccount(accountId);
        validateBalancePolicy(request.accountType(), account.getCurrentBalance(), request.creditLimit());
        account.update(
                request.name(),
                request.accountType(),
                request.institutionName(),
                request.currency(),
                request.creditLimit()
        );

        return AccountResponse.from(accountRepository.save(account));
    }

    @Transactional
    public void deactivate(UUID accountId) {
        FinancialAccount account = lockOwnedAccount(accountId);
        account.deactivate();
        accountRepository.save(account);
    }

    private FinancialAccount findOwnedAccount(UUID accountId) {
        User user = getAuthenticatedUser();
        return accountRepository.findByIdAndUserId(accountId, user.getId())
                .orElseThrow(AccountNotFoundException::new);
    }

    private FinancialAccount lockOwnedAccount(UUID accountId) {
        User user = getAuthenticatedUser();
        return accountRepository.findByIdAndUserIdForUpdate(accountId, user.getId())
                .orElseThrow(AccountNotFoundException::new);
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

    private void validateCreditLimit(AccountType accountType, BigDecimal creditLimit) {
        if (creditLimit != null && creditLimit.signum() < 0) {
            throw new InvalidCreditLimitException("Credit limit must not be negative");
        }
        if (accountType != AccountType.CREDIT_CARD
                && creditLimit != null
                && creditLimit.signum() > 0) {
            throw new InvalidCreditLimitException(
                    "A positive credit limit is only allowed for credit card accounts"
            );
        }
    }

    private void validateBalancePolicy(AccountType accountType, BigDecimal balance, BigDecimal creditLimit) {
        if (accountType != AccountType.CREDIT_CARD && balance.signum() < 0) {
            throw new InvalidAccountBalanceException("This account type does not support a negative balance");
        }
        if (accountType == AccountType.CREDIT_CARD
                && creditLimit != null
                && balance.signum() < 0
                && balance.abs().compareTo(creditLimit) > 0) {
            throw new InvalidAccountBalanceException("Current credit usage exceeds the configured credit limit");
        }
    }
}
