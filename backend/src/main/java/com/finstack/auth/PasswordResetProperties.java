package com.finstack.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Validated
@ConfigurationProperties(prefix = "app.password-reset")
public record PasswordResetProperties(@Min(5) @Max(60) long expirationMinutes, boolean developmentLogLink) { }
