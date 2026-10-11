package com.finstack.user;

import com.finstack.role.Role;
import com.finstack.role.RoleName;
import com.finstack.security.CustomUserDetailsService;
import com.finstack.security.JwtAuthenticationFilter;
import com.finstack.security.JwtService;
import com.finstack.security.RestAccessDeniedHandler;
import com.finstack.security.RestAuthenticationEntryPoint;
import com.finstack.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({
        SecurityConfig.class,
        JwtService.class,
        JwtAuthenticationFilter.class,
        CustomUserDetailsService.class,
        RestAuthenticationEntryPoint.class,
        RestAccessDeniedHandler.class
})
@TestPropertySource(properties = {
        "jwt.secret=test-secret-that-is-at-least-thirty-two-bytes-long",
        "jwt.access-token-expiration-ms=3600000"
})
class UserControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void rejectsAProtectedRequestWithoutAJwt() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void rejectsAProtectedRequestWithAnInvalidJwt() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.jwt.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void returnsTheAuthenticatedUserWithoutThePasswordHash() throws Exception {
        User alice = new User("Ada", "Lovelace", "ada@example.com", "secret-password-hash", UserStatus.ACTIVE);
        alice.addRole(new Role(RoleName.ROLE_USER));
        User anotherUser = new User("Grace", "Hopper", "grace@example.com", "another-password-hash", UserStatus.ACTIVE);
        anotherUser.addRole(new Role(RoleName.ROLE_ADMIN));
        when(userRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(alice));
        when(userRepository.findByEmailIgnoreCase("grace@example.com")).thenReturn(Optional.of(anotherUser));
        String token = jwtService.generateAccessToken(alice);

        mockMvc.perform(get("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("ada@example.com"))
                .andExpect(jsonPath("$.firstName").value("Ada"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_USER"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void rejectsPreviouslyIssuedJwtAfterPasswordReset() throws Exception {
        User user = new User("Ada", "Lovelace", "ada@example.com", "old-hash", UserStatus.ACTIVE);
        user.addRole(new Role(RoleName.ROLE_USER));
        when(userRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));
        String priorToken = jwtService.generateAccessToken(user);
        user.resetPassword("replacement-hash");

        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + priorToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }
}
