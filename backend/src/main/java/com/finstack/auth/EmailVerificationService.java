package com.finstack.auth;

import com.finstack.user.User;
import com.finstack.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;

@Service
public class EmailVerificationService {
    private final EmailVerificationTokenRepository tokens; private final UserRepository users; private final EmailDeliveryService delivery; private final EmailVerificationProperties properties; private final SecureRandom random = new SecureRandom();
    public EmailVerificationService(EmailVerificationTokenRepository tokens, UserRepository users, EmailDeliveryService delivery, EmailVerificationProperties properties) { this.tokens=tokens; this.users=users; this.delivery=delivery; this.properties=properties; }
    @Transactional public void issueFor(User user) { if (user.isEmailVerified()) return; tokens.deleteByUserIdAndUsedAtIsNull(user.getId()); byte[] bytes=new byte[32]; random.nextBytes(bytes); String raw=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); tokens.save(new EmailVerificationToken(user, hash(raw), OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(properties.expirationMinutes()))); delivery.sendVerification(user, raw); }
    @Transactional public boolean verify(String rawToken) { if (rawToken == null || rawToken.isBlank()) return false; return tokens.findByTokenHash(hash(rawToken)).filter(token -> token.isUsable(OffsetDateTime.now(ZoneOffset.UTC))).map(token -> { token.getUser().verifyEmail(); token.consume(); users.save(token.getUser()); return true; }).orElse(false); }
    private String hash(String value) { try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception exception) { throw new IllegalStateException("Unable to hash verification token", exception); } }
}
