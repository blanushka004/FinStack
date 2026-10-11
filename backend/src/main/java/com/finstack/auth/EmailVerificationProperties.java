package com.finstack.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.email-verification")
public record EmailVerificationProperties(long expirationMinutes, String frontendBaseUrl, boolean developmentLogLink) { }
