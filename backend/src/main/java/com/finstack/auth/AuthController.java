package com.finstack.auth;

import com.finstack.auth.dto.AuthResponse;
import com.finstack.auth.dto.LoginRequest;
import com.finstack.auth.dto.RegisterRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.finstack.auth.dto.RequestPasswordReset;
import com.finstack.auth.dto.ConfirmPasswordReset;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordResetService passwordResetService;

    public AuthController(AuthService authService, PasswordResetService passwordResetService) {
        this.authService = authService;
        this.passwordResetService = passwordResetService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        return ResponseEntity.ok(authService.authenticate(request, servletRequest.getRemoteAddr(), servletRequest.getHeader("User-Agent")));
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<Void> resendVerification(@Valid @RequestBody com.finstack.auth.dto.ResendVerificationRequest request) {
        authService.resendVerification(request.email());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/verify-email")
    public ResponseEntity<com.finstack.auth.dto.EmailVerificationResponse> verifyEmail(@RequestParam(required = false) String token) {
        return ResponseEntity.ok(new com.finstack.auth.dto.EmailVerificationResponse(authService.verifyEmail(token)));
    }

    @PostMapping("/password-reset/request")
    public ResponseEntity<java.util.Map<String, String>> requestPasswordReset(@Valid @RequestBody RequestPasswordReset request,
                                                                               HttpServletRequest servletRequest) {
        passwordResetService.request(request.email(), servletRequest.getRemoteAddr());
        return ResponseEntity.ok(java.util.Map.of("message", "If an account exists for that email, a reset link has been sent."));
    }

    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirmPasswordReset(@Valid @RequestBody ConfirmPasswordReset request) {
        passwordResetService.confirm(request.token(), request.password());
        return ResponseEntity.noContent().build();
    }
}
