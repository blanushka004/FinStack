package com.finstack.auth;

import com.finstack.user.User;
import com.finstack.user.UserRepository;
import com.finstack.user.UserStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {
    @Mock EmailVerificationTokenRepository tokens;
    @Mock UserRepository users;
    @Mock EmailDeliveryService delivery;
    private EmailVerificationService service;

    @BeforeEach void setUp() { service = new EmailVerificationService(tokens, users, delivery, new EmailVerificationProperties(30, "http://localhost:5173", false)); }

    @Test void issuesOnlyAHashAndVerifiesTheMatchingRawTokenOnce() throws Exception {
        User user = new User("Ada", "Lovelace", "ada@example.com", "hash", UserStatus.ACTIVE);
        service.issueFor(user);
        ArgumentCaptor<EmailVerificationToken> token = ArgumentCaptor.forClass(EmailVerificationToken.class);
        ArgumentCaptor<String> raw = ArgumentCaptor.forClass(String.class);
        verify(tokens).save(token.capture()); verify(delivery).sendVerification(eq(user), raw.capture());
        assertThat(token.getValue().getTokenHash()).isNotEqualTo(raw.getValue()).isEqualTo(sha256(raw.getValue()));
        when(tokens.findByTokenHash(sha256(raw.getValue()))).thenReturn(Optional.of(token.getValue()));
        assertThat(service.verify(raw.getValue())).isTrue(); assertThat(user.isEmailVerified()).isTrue(); verify(users).save(user);
        assertThat(service.verify(raw.getValue())).isFalse();
    }

    @Test void rejectsExpiredAndInvalidTokens() throws Exception {
        User user = new User("Ada", "Lovelace", "ada@example.com", "hash", UserStatus.ACTIVE);
        String raw = "not-persisted-raw-token";
        when(tokens.findByTokenHash(sha256(raw))).thenReturn(Optional.of(new EmailVerificationToken(user, sha256(raw), OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(1))));
        assertThat(service.verify(raw)).isFalse(); assertThat(service.verify("unknown-token")).isFalse(); verify(users, never()).save(any());
    }

    @Test void resendInvalidatesPriorActiveTokenAndSkipsVerifiedUsers() {
        User user = new User("Ada", "Lovelace", "ada@example.com", "hash", UserStatus.ACTIVE);
        service.issueFor(user); service.issueFor(user);
        verify(tokens, times(2)).deleteByUserIdAndUsedAtIsNull(user.getId()); verify(tokens, times(2)).save(any());
        user.verifyEmail(); service.issueFor(user); verify(tokens, times(2)).save(any());
    }

    private static String sha256(String value) throws Exception { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
}
