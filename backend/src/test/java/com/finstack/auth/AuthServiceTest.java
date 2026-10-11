package com.finstack.auth;

import com.finstack.auth.dto.AuthResponse;
import com.finstack.auth.dto.LoginRequest;
import com.finstack.auth.dto.RegisterRequest;
import com.finstack.role.Role;
import com.finstack.role.RoleName;
import com.finstack.role.RoleRepository;
import com.finstack.user.User;
import com.finstack.user.UserRepository;
import com.finstack.security.JwtService;
import com.finstack.audit.LoginAuditService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtService jwtService;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private LoginAuditService loginAuditService;

    private BCryptPasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4);
        authService = new AuthService(userRepository, roleRepository, passwordEncoder, authenticationManager, jwtService, emailVerificationService, loginAuditService);
    }

    @Test
    void registersAnActiveUserWithABcryptPasswordAndUserRole() {
        Role userRole = new Role(RoleName.ROLE_USER);
        RegisterRequest request = new RegisterRequest(
                "Ada", "Lovelace", " ADA@EXAMPLE.COM ", "Correct-Horse1!"
        );
        when(userRepository.existsByEmailIgnoreCase("ada@example.com")).thenReturn(false);
        when(roleRepository.findByName(RoleName.ROLE_USER)).thenReturn(Optional.of(userRole));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("access-token");
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(3600L);

        AuthResponse response = authService.register(request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();

        assertThat(savedUser.getEmail()).isEqualTo("ada@example.com");
        assertThat(savedUser.getPasswordHash()).isNotEqualTo(request.password());
        assertThat(passwordEncoder.matches(request.password(), savedUser.getPasswordHash())).isTrue();
        assertThat(savedUser.getRoles()).containsExactly(userRole);
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresIn()).isEqualTo(3600L);
        assertThat(response.user().email()).isEqualTo("ada@example.com");
    }

    @Test
    void rejectsDuplicateRegistration() {
        RegisterRequest request = new RegisterRequest(
                "Ada", "Lovelace", "ada@example.com", "Correct-Horse1!"
        );
        when(userRepository.existsByEmailIgnoreCase("ada@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(EmailAlreadyExistsException.class);

        verify(userRepository, never()).save(any(User.class));
        verify(roleRepository, never()).findByName(any());
    }

    @Test
    void authenticatesCorrectCredentials() {
        LoginRequest request = new LoginRequest(" ADA@EXAMPLE.COM ", "Correct-Horse1!");
        User user = new User("Ada", "Lovelace", "ada@example.com", "password-hash", com.finstack.user.UserStatus.ACTIVE);
        when(authenticationManager.authenticate(any())).thenReturn(
                UsernamePasswordAuthenticationToken.authenticated("ada@example.com", null, List.of())
        );
        when(userRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));
        when(userRepository.save(user)).thenReturn(user);
        when(jwtService.generateAccessToken(user)).thenReturn("access-token");
        when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(3600L);

        AuthResponse response = authService.authenticate(request);

        ArgumentCaptor<UsernamePasswordAuthenticationToken> tokenCaptor =
                ArgumentCaptor.forClass(UsernamePasswordAuthenticationToken.class);
        verify(authenticationManager).authenticate(tokenCaptor.capture());
        assertThat(tokenCaptor.getValue().getPrincipal()).isEqualTo("ada@example.com");
        verify(userRepository).save(user);
        assertThat(user.getLastLoginAt()).isNotNull();
        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.user().email()).isEqualTo("ada@example.com");
    }

    @Test
    void rejectsInvalidCredentialsWithoutLeakingDetails() {
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("bad credentials"));

        assertThatThrownBy(() -> authService.authenticate(new LoginRequest("ada@example.com", "wrong-password")))
                .isInstanceOf(InvalidCredentialsException.class)
                .hasMessage("Invalid email or password");
        verify(loginAuditService).record(eq(null), eq("ada@example.com"), eq(false), eq(null), eq(null), eq("INVALID_CREDENTIALS"));
    }

    @Test
    void rejectsWeakPasswordBeforePersistingAUser() {
        RegisterRequest request = new RegisterRequest("Ada", "Lovelace", "ada@example.com", "lowercase1!");
        assertThatThrownBy(() -> authService.register(request)).isInstanceOf(WeakPasswordException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void allowsUnverifiedUsersToLoginAndAuditsTheNormalizedSuccess() {
        User user = new User("Ada", "Lovelace", "ada@example.com", "hash", com.finstack.user.UserStatus.ACTIVE);
        when(authenticationManager.authenticate(any())).thenReturn(UsernamePasswordAuthenticationToken.authenticated("ada@example.com", null, List.of()));
        when(userRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));
        when(jwtService.generateAccessToken(user)).thenReturn("access-token"); when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(3600L);
        AuthResponse response = authService.authenticate(new LoginRequest(" ADA@EXAMPLE.COM ", "anything"), "127.0.0.1", "test-agent");
        assertThat(response.user().emailVerified()).isFalse();
        verify(loginAuditService).record(user, "ada@example.com", true, "127.0.0.1", "test-agent", null);
    }
}
