package com.finstack.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, UUID> {
    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update PasswordResetToken token set token.usedAt = :now where token.id = :id and token.usedAt is null and token.expiresAt > :now")
    int consumeIfUsable(@Param("id") UUID id, @Param("now") OffsetDateTime now);

    @Modifying
    @Query("update PasswordResetToken token set token.usedAt = :now where token.user.id = :userId and token.usedAt is null and token.id <> :exceptId")
    int invalidateOthers(@Param("userId") UUID userId, @Param("exceptId") UUID exceptId, @Param("now") OffsetDateTime now);

    @Modifying
    @Query("update PasswordResetToken token set token.usedAt = :now where token.user.id = :userId and token.usedAt is null")
    int invalidateAll(@Param("userId") UUID userId, @Param("now") OffsetDateTime now);
}
