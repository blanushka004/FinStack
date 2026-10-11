package com.finstack.auth;

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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {
    @Mock UserRepository users;
    @Mock PasswordResetTokenRepository tokens;
    @Mock EmailDeliveryService delivery;
    private BCryptPasswordEncoder encoder;
    private PasswordResetService service;
    private User user;

    @BeforeEach void setup() {
        encoder = new BCryptPasswordEncoder(4);
        service = new PasswordResetService(users, tokens, encoder, delivery,
                new PasswordResetProperties(30, false), new PasswordResetRateLimiter());
        user = new User("Ada", "Lovelace", "ada@example.com", encoder.encode("Original1!"), UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach void cleanup() { TransactionSynchronizationManager.clearSynchronization(); }

    @Test void unknownEmailHasNoDeliveryAndRequestNormalizesKnownEmail() {
        service.request(" UNKNOWN@example.com ", "127.0.0.1");
        verify(users).findByEmailIgnoreCase("unknown@example.com");
        verify(tokens, never()).save(any());
        verifyNoInteractions(delivery);

        when(users.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        service.request(" ADA@EXAMPLE.COM ", "127.0.0.1");
        ArgumentCaptor<PasswordResetToken> saved = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(tokens).save(saved.capture());
        verify(tokens).invalidateAll(eq(user.getId()), any());
        assertThat(saved.getValue().getTokenHash()).hasSize(64);
        verifyNoInteractions(delivery); // Never deliver a link before commit.
        for (TransactionSynchronization callback : TransactionSynchronizationManager.getSynchronizations()) callback.afterCommit();
        ArgumentCaptor<String> raw = ArgumentCaptor.forClass(String.class);
        verify(delivery).sendPasswordReset(eq(user), raw.capture());
        assertThat(saved.getValue().getTokenHash()).isEqualTo(PasswordResetService.hash(raw.getValue())).isNotEqualTo(raw.getValue());
    }

    @Test void validTokenChangesOnlyItsOwnerPasswordAndCannotBeReused() {
        String oldHash = user.getPasswordHash();
        User other = new User("Other", "Person", "other@example.com", encoder.encode("OtherPass1!"), UserStatus.ACTIVE);
        String otherHash = other.getPasswordHash();
        String raw = "valid-raw-token";
        PasswordResetToken token = new PasswordResetToken(user, PasswordResetService.hash(raw), OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(30));
        ReflectionTestUtils.setField(token, "id", UUID.randomUUID());
        when(tokens.findByTokenHash(token.getTokenHash())).thenReturn(Optional.of(token));
        when(tokens.consumeIfUsable(eq(token.getId()), any())).thenReturn(1, 0);
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));

        service.confirm(raw, "Replacement2!");
        assertThat(user.getPasswordHash()).isNotEqualTo(oldHash);
        assertThat(encoder.matches("Replacement2!", user.getPasswordHash())).isTrue();
        assertThat(encoder.matches("Original1!", user.getPasswordHash())).isFalse();
        assertThat(user.getTokenVersion()).isEqualTo(1);
        assertThat(other.getPasswordHash()).isEqualTo(otherHash);
        verify(tokens).invalidateOthers(eq(user.getId()), eq(token.getId()), any());
        assertThatThrownBy(() -> service.confirm(raw, "Replacement3!"))
                .isInstanceOf(InvalidPasswordResetTokenException.class);
        assertThat(user.getTokenVersion()).isEqualTo(1);
    }

    @Test void expiredInvalidAndWeakPasswordCannotChangeStoredPassword() {
        String oldHash = user.getPasswordHash();
        String raw = "expired-token";
        PasswordResetToken expired = new PasswordResetToken(user, PasswordResetService.hash(raw), OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        ReflectionTestUtils.setField(expired, "id", UUID.randomUUID());
        when(tokens.findByTokenHash(expired.getTokenHash())).thenReturn(Optional.of(expired));
        when(users.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        assertThatThrownBy(() -> service.confirm(raw, "Replacement2!"))
                .isInstanceOf(ExpiredPasswordResetTokenException.class);
        assertThatThrownBy(() -> service.confirm("missing", "Replacement2!"))
                .isInstanceOf(InvalidPasswordResetTokenException.class);
        assertThatThrownBy(() -> service.confirm(raw, "weak"))
                .isInstanceOf(WeakPasswordException.class);
        assertThat(user.getPasswordHash()).isEqualTo(oldHash);
    }

    @Test void throttlesRepeatedRequestsForKnownAndUnknownAddresses() {
        for (int i = 0; i < 8; i++) service.request("unknown@example.com", "127.0.0.1");
        verify(users, times(5)).findByEmailIgnoreCase("unknown@example.com");
    }
}
