package com.finstack.auth;

import com.finstack.auth.dto.AuthResponse;
import com.finstack.auth.dto.LoginRequest;
import com.finstack.auth.dto.RegisterRequest;
import com.finstack.role.Role;
import com.finstack.role.RoleName;
import com.finstack.role.RoleRepository;
import com.finstack.user.User;
import com.finstack.user.UserRepository;
import com.finstack.user.UserStatus;
import com.finstack.user.dto.UserResponse;
import com.finstack.security.JwtService;
import com.finstack.audit.LoginAuditService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final EmailVerificationService emailVerificationService;
    private final LoginAuditService loginAuditService;

    public AuthService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService,
            EmailVerificationService emailVerificationService,
            LoginAuditService loginAuditService
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.emailVerificationService = emailVerificationService;
        this.loginAuditService = loginAuditService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());

        if (!PasswordPolicy.isValid(request.password())) {
            throw new WeakPasswordException();
        }

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyExistsException();
        }

        Role userRole = roleRepository.findByName(RoleName.ROLE_USER)
                .orElseThrow(() -> new RoleNotFoundException(RoleName.ROLE_USER.name()));

        User user = new User(
                request.firstName().trim(),
                request.lastName().trim(),
                email,
                passwordEncoder.encode(request.password()),
                UserStatus.ACTIVE
        );
        user.addRole(userRole);
        userRepository.save(user);
        emailVerificationService.issueFor(user);

        return createAuthResponse(user);
    }

    @Transactional
    public void resendVerification(String email) {
        userRepository.findByEmailIgnoreCase(normalizeEmail(email)).ifPresent(emailVerificationService::issueFor);
    }

    @Transactional
    public boolean verifyEmail(String token) {
        return emailVerificationService.verify(token);
    }

    @Transactional
    public AuthResponse authenticate(LoginRequest request, String ipAddress, String userAgent) {
        String email = normalizeEmail(request.email());

        try {
            authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(email, request.password())
            );
        } catch (AuthenticationException exception) {
            loginAuditService.record(null, email, false, ipAddress, userAgent, "INVALID_CREDENTIALS");
            throw new InvalidCredentialsException();
        }

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(InvalidCredentialsException::new);
        user.recordLogin();
        userRepository.save(user);
        loginAuditService.record(user, email, true, ipAddress, userAgent, null);

        return createAuthResponse(user);
    }

    public AuthResponse authenticate(LoginRequest request) {
        return authenticate(request, null, null);
    }

    private AuthResponse createAuthResponse(User user) {
        return new AuthResponse(
                jwtService.generateAccessToken(user),
                "Bearer",
                jwtService.getAccessTokenExpirationSeconds(),
                UserResponse.from(user)
        );
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
