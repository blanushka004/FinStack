package com.finstack.user.dto;

import com.finstack.user.User;
import com.finstack.user.UserStatus;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String firstName,
        String lastName,
        String email,
        UserStatus status,
        boolean emailVerified,
        Set<String> roles,
        OffsetDateTime createdAt,
        OffsetDateTime lastLoginAt
) {

    public static UserResponse from(User user) {
        Set<String> roles = user.getRoles().stream()
                .map(role -> role.getName().name())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        return new UserResponse(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getStatus(),
                user.isEmailVerified(),
                roles,
                user.getCreatedAt(),
                user.getLastLoginAt()
        );
    }
}
