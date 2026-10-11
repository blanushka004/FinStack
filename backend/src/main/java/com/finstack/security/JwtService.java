package com.finstack.security;

import com.finstack.user.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Optional;

@Service
public class JwtService {

    private static final int HS256_MINIMUM_KEY_BYTES = 32;

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        byte[] secretBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < HS256_MINIMUM_KEY_BYTES) {
            throw new IllegalStateException("JWT secret must be at least 32 bytes long");
        }
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
    }

    public String generateAccessToken(User user) {
        Instant now = Instant.now();
        List<String> roles = user.getRoles().stream()
                .map(role -> role.getName().name())
                .sorted()
                .toList();

        return Jwts.builder()
                .subject(user.getEmail())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(properties.accessTokenExpirationMs())))
                .claim("uid", user.getId() == null ? null : user.getId().toString())
                .claim("roles", roles)
                .claim("tv", user.getTokenVersion())
                .signWith(signingKey, Jwts.SIG.HS256)
                .compact();
    }

    public boolean isTokenValid(String token) {
        return parseClaims(token).isPresent();
    }

    public Optional<String> extractSubject(String token) {
        return parseClaims(token).map(Claims::getSubject);
    }

    public boolean matchesTokenVersion(String token, long currentVersion) {
        return parseClaims(token).map(claims -> {
            Object version = claims.get("tv");
            // Tokens issued before V9 are version zero and cease working after a reset.
            return version == null ? currentVersion == 0
                    : version instanceof Number number && number.longValue() == currentVersion;
        }).orElse(false);
    }

    private Optional<Claims> parseClaims(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    public long getAccessTokenExpirationSeconds() {
        return properties.accessTokenExpirationMs() / 1_000;
    }
}
