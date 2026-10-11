package com.finstack.auth.dto;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
public record ResendVerificationRequest(@NotBlank @Email String email) { public ResendVerificationRequest { email = email == null ? null : email.trim(); } }
