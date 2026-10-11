package com.finstack.auth;

import com.finstack.user.User;
import jakarta.persistence.*;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "email_verification_tokens")
public class EmailVerificationToken {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64) private String tokenHash;
    @Column(name = "expires_at", nullable = false) private OffsetDateTime expiresAt;
    @Column(name = "used_at") private OffsetDateTime usedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private OffsetDateTime createdAt;
    protected EmailVerificationToken() { }
    EmailVerificationToken(User user, String tokenHash, OffsetDateTime expiresAt) { this.user=user; this.tokenHash=tokenHash; this.expiresAt=expiresAt; }
    @PrePersist void created() { if (createdAt == null) createdAt = OffsetDateTime.now(java.time.ZoneOffset.UTC); }
    public User getUser() { return user; }
    public String getTokenHash() { return tokenHash; }
    public boolean isUsable(OffsetDateTime now) { return usedAt == null && expiresAt.isAfter(now); }
    public void consume() { usedAt = OffsetDateTime.now(java.time.ZoneOffset.UTC); }
}
