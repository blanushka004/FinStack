package com.finstack.account;

import com.finstack.account.dto.AccountCreateRequest;
import com.finstack.account.dto.AccountResponse;
import com.finstack.account.dto.AccountUpdateRequest;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FinancialAccountServiceTest {

    @Mock
    private FinancialAccountRepository accountRepository;

    @Mock
    private UserRepository userRepository;

    private FinancialAccountService accountService;
    private User currentUser;
    private UUID currentUserId;

    @BeforeEach
    void setUp() {
        accountService = new FinancialAccountService(accountRepository, userRepository);
        currentUserId = UUID.randomUUID();
        currentUser = new User("Ada", "Lovelace", "ada@example.com", "password-hash", UserStatus.ACTIVE);
        ReflectionTestUtils.setField(currentUser, "id", currentUserId);
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
    void createsAnAccountWithDefaultInrCurrency() {
        AccountCreateRequest request = new AccountCreateRequest(
                " Main wallet ", AccountType.WALLET, " Cash box ", null, new BigDecimal("1250.50"), null
        );
        when(accountRepository.save(any(FinancialAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccountResponse response = accountService.create(request);

        ArgumentCaptor<FinancialAccount> accountCaptor = ArgumentCaptor.forClass(FinancialAccount.class);
        verify(accountRepository).save(accountCaptor.capture());
        FinancialAccount savedAccount = accountCaptor.getValue();
        assertThat(savedAccount.getUser()).isSameAs(currentUser);
        assertThat(savedAccount.getName()).isEqualTo("Main wallet");
        assertThat(savedAccount.getCurrency()).isEqualTo("INR");
        assertThat(savedAccount.isActive()).isTrue();
        assertThat(response.currency()).isEqualTo("INR");
    }

    @Test
    void createsCreditCardWithAValidCreditLimit() {
        AccountCreateRequest request = new AccountCreateRequest(
                "Travel card", AccountType.CREDIT_CARD, "FinBank", "usd",
                new BigDecimal("-500.00"), new BigDecimal("100000.00")
        );
        when(accountRepository.save(any(FinancialAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AccountResponse response = accountService.create(request);

        assertThat(response.accountType()).isEqualTo(AccountType.CREDIT_CARD);
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.creditLimit()).isEqualByComparingTo("100000.00");
    }

    @Test
    void rejectsPositiveCreditLimitForANonCreditCard() {
        AccountCreateRequest request = new AccountCreateRequest(
                "Savings", AccountType.SAVINGS_ACCOUNT, null, "INR",
                BigDecimal.ZERO, new BigDecimal("1000.00")
        );

        assertThatThrownBy(() -> accountService.create(request))
                .isInstanceOf(InvalidCreditLimitException.class);

        verify(accountRepository, never()).save(any(FinancialAccount.class));
    }

    @Test
    void listsOnlyTheCurrentUsersAccounts() {
        FinancialAccount account = account("Wallet", AccountType.WALLET);
        when(accountRepository.findAllByUserIdAndActive(currentUserId, true)).thenReturn(List.of(account));

        List<AccountResponse> accounts = accountService.list(true);

        verify(accountRepository).findAllByUserIdAndActive(currentUserId, true);
        assertThat(accounts).extracting(AccountResponse::name).containsExactly("Wallet");
    }

    @Test
    void retrievesAnOwnedAccount() {
        UUID accountId = UUID.randomUUID();
        FinancialAccount account = account("Wallet", AccountType.WALLET);
        when(accountRepository.findByIdAndUserId(accountId, currentUserId)).thenReturn(Optional.of(account));

        AccountResponse response = accountService.getById(accountId);

        assertThat(response.name()).isEqualTo("Wallet");
    }

    @Test
    void doesNotRetrieveAnotherUsersAccount() {
        UUID anotherUsersAccountId = UUID.randomUUID();
        when(accountRepository.findByIdAndUserId(anotherUsersAccountId, currentUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.getById(anotherUsersAccountId))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void doesNotUpdateAnotherUsersAccount() {
        UUID anotherUsersAccountId = UUID.randomUUID();
        when(accountRepository.findByIdAndUserIdForUpdate(anotherUsersAccountId, currentUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.update(anotherUsersAccountId, updateRequest()))
                .isInstanceOf(AccountNotFoundException.class);

        verify(accountRepository, never()).save(any(FinancialAccount.class));
    }

    @Test
    void doesNotDeleteAnotherUsersAccount() {
        UUID anotherUsersAccountId = UUID.randomUUID();
        when(accountRepository.findByIdAndUserIdForUpdate(anotherUsersAccountId, currentUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.deactivate(anotherUsersAccountId))
                .isInstanceOf(AccountNotFoundException.class);

        verify(accountRepository, never()).save(any(FinancialAccount.class));
    }

    @Test
    void softDeleteMarksAnOwnedAccountInactive() {
        UUID accountId = UUID.randomUUID();
        FinancialAccount account = account("Wallet", AccountType.WALLET);
        when(accountRepository.findByIdAndUserIdForUpdate(accountId, currentUserId)).thenReturn(Optional.of(account));
        when(accountRepository.save(account)).thenReturn(account);

        accountService.deactivate(accountId);

        assertThat(account.isActive()).isFalse();
        verify(accountRepository).save(account);
    }

    @Test
    void accountMetadataUpdatePreservesTransactionManagedBalance() {
        UUID accountId = UUID.randomUUID();
        FinancialAccount account = new FinancialAccount(
                currentUser, "Wallet", AccountType.WALLET, null, "INR", new BigDecimal("500.00"), null
        );
        when(accountRepository.findByIdAndUserIdForUpdate(accountId, currentUserId)).thenReturn(Optional.of(account));
        when(accountRepository.save(account)).thenReturn(account);

        accountService.update(accountId, updateRequest());

        assertThat(account.getCurrentBalance()).isEqualByComparingTo("500.00");
        verify(accountRepository).findByIdAndUserIdForUpdate(accountId, currentUserId);
    }

    private FinancialAccount account(String name, AccountType accountType) {
        return new FinancialAccount(
                currentUser, name, accountType, null, "INR", BigDecimal.ZERO, null
        );
    }

    private AccountUpdateRequest updateRequest() {
        return new AccountUpdateRequest(
                "Updated account", AccountType.WALLET, null, "INR", null
        );
    }
}
