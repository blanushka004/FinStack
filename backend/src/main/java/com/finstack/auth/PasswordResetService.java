package com.finstack.auth;

import com.finstack.user.User;
import com.finstack.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class PasswordResetService {
    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final EmailDeliveryService delivery;
    private final PasswordResetProperties properties;
    private final PasswordResetRateLimiter limiter;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(UserRepository users, PasswordResetTokenRepository tokens, PasswordEncoder encoder,
                                EmailDeliveryService delivery, PasswordResetProperties properties, PasswordResetRateLimiter limiter) {
        this.users = users;
        this.tokens = tokens;
        this.encoder = encoder;
        this.delivery = delivery;
        this.properties = properties;
        this.limiter = limiter;
    }

    @Transactional
    public void request(String email, String remoteAddress) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (!limiter.allow(normalized, remoteAddress == null ? "unknown" : remoteAddress)) return;
        users.findByEmailIgnoreCase(normalized).ifPresent(found -> {
            User user = users.findByIdForUpdate(found.getId()).orElseThrow();
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            tokens.invalidateAll(user.getId(), now);
            byte[] bytes = new byte[32];
            random.nextBytes(bytes);
            String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            tokens.save(new PasswordResetToken(user, hash(raw), now.plusMinutes(properties.expirationMinutes())));
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { delivery.sendPasswordReset(user, raw); }
            });
        });
    }

    @Transactional
    public void confirm(String rawToken, String password) {
        if (!PasswordPolicy.isValid(password)) throw new WeakPasswordException();
        if (rawToken == null || rawToken.length() > 256 || rawToken.isBlank()) throw new InvalidPasswordResetTokenException();
        PasswordResetToken candidate = tokens.findByTokenHash(hash(rawToken)).orElseThrow(InvalidPasswordResetTokenException::new);
        User user = users.findByIdForUpdate(candidate.getUser().getId()).orElseThrow(InvalidPasswordResetTokenException::new);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        if (candidate.isExpired(now)) throw new ExpiredPasswordResetTokenException();
        if (tokens.consumeIfUsable(candidate.getId(), now) != 1) throw new InvalidPasswordResetTokenException();
        user.resetPassword(encoder.encode(password));
        tokens.invalidateOthers(user.getId(), candidate.getId(), now);
        // Dirty checking commits the password change and token consumption atomically.
    }

    static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
