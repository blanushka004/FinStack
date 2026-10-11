package com.finstack.auth;

import com.finstack.user.User;
import com.finstack.user.UserRepository;
import com.finstack.user.UserStatus;
import com.finstack.security.CustomUserDetailsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.*;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.verify;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest(properties = {"spring.flyway.enabled=false", "spring.jpa.hibernate.ddl-auto=create-drop"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({PasswordResetService.class, PasswordResetRateLimiter.class, CustomUserDetailsService.class, PasswordResetPersistenceTest.Config.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PasswordResetPersistenceTest {
    @TestConfiguration static class Config {
        @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(4); }
    }
    @Autowired UserRepository users;
    @Autowired PasswordResetTokenRepository tokens;
    @Autowired PasswordResetService service;
    @Autowired PasswordEncoder encoder;
    @Autowired CustomUserDetailsService userDetails;
    @MockitoBean EmailDeliveryService delivery;

    @Test void concurrentUseOfOneTokenChangesPasswordOnlyOnce() throws Exception {
        User user = users.save(new User("Ada", "Lovelace", "concurrent-reset@example.com", encoder.encode("Original1!"), UserStatus.ACTIVE));
        String raw = "concurrent-test-token";
        tokens.save(new PasswordResetToken(user, PasswordResetService.hash(raw), OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(30)));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<Boolean> first = pool.submit(() -> attempt(start, raw));
            Future<Boolean> second = pool.submit(() -> attempt(start, raw));
            start.countDown();
            assertThat((first.get(10, TimeUnit.SECONDS) ? 1 : 0) + (second.get(10, TimeUnit.SECONDS) ? 1 : 0)).isEqualTo(1);
            User updated = users.findById(user.getId()).orElseThrow();
            assertThat(encoder.matches("Replacement2!", updated.getPasswordHash())).isTrue();
            assertThat(encoder.matches("Original1!", updated.getPasswordHash())).isFalse();
            assertThat(updated.getTokenVersion()).isEqualTo(1);
            assertThat(tokens.findByTokenHash(PasswordResetService.hash(raw)).orElseThrow().isUsable(OffsetDateTime.now(ZoneOffset.UTC))).isFalse();
            DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetails);
            provider.setPasswordEncoder(encoder);
            assertThat(provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(user.getEmail(), "Replacement2!")).isAuthenticated()).isTrue();
            assertThatThrownBy(() -> provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(user.getEmail(), "Original1!")))
                    .isInstanceOf(org.springframework.security.core.AuthenticationException.class);
        } finally { pool.shutdownNow(); }
    }

    private boolean attempt(CountDownLatch start, String raw) throws InterruptedException {
        start.await();
        try { service.confirm(raw, "Replacement2!"); return true; }
        catch (InvalidPasswordResetTokenException exception) { return false; }
    }

    @Test void newRequestInvalidatesPriorUnusedLinkInDatabase() {
        User user = users.save(new User("Ada", "Lovelace", "new-request@example.com", encoder.encode("Original1!"), UserStatus.ACTIVE));
        String oldRaw = "older-reset-link";
        tokens.save(new PasswordResetToken(user, PasswordResetService.hash(oldRaw), OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(30)));

        service.request(" NEW-REQUEST@example.com ", "127.0.0.1");

        PasswordResetToken oldToken = tokens.findByTokenHash(PasswordResetService.hash(oldRaw)).orElseThrow();
        assertThat(oldToken.isUsable(OffsetDateTime.now(ZoneOffset.UTC))).isFalse();
        assertThatThrownBy(() -> service.confirm(oldRaw, "Replacement2!"))
                .isInstanceOf(InvalidPasswordResetTokenException.class);
        ArgumentCaptor<String> issued = ArgumentCaptor.forClass(String.class);
        verify(delivery).sendPasswordReset(org.mockito.ArgumentMatchers.any(User.class), issued.capture());
        assertThat(tokens.findByTokenHash(PasswordResetService.hash(issued.getValue()))).isPresent();
    }
}
