package com.finstack.audit;

import com.finstack.role.Role;
import com.finstack.role.RoleName;
import com.finstack.security.*;
import com.finstack.user.User;
import com.finstack.user.UserRepository;
import com.finstack.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdminLoginAuditController.class)
@Import({SecurityConfig.class, JwtService.class, JwtAuthenticationFilter.class, CustomUserDetailsService.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
@TestPropertySource(properties = {"jwt.secret=test-secret-that-is-at-least-thirty-two-bytes-long", "jwt.access-token-expiration-ms=3600000"})
class AdminLoginAuditControllerSecurityTest {
    @Autowired MockMvc mockMvc;
    @Autowired JwtService jwtService;
    @MockitoBean LoginAuditRepository auditRepository;
    @MockitoBean UserRepository userRepository;

    @Test void rejectsAnonymousAndUserButAllowsAdminHistoryAndSummary() throws Exception {
        mockMvc.perform(get("/api/v1/admin/login-audit")).andExpect(status().isUnauthorized());
        User standardUser = user("user@example.com", RoleName.ROLE_USER); when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(standardUser));
        mockMvc.perform(get("/api/v1/admin/login-audit").header("Authorization", "Bearer " + jwtService.generateAccessToken(standardUser))).andExpect(status().isForbidden());
        User admin = user("admin@example.com", RoleName.ROLE_ADMIN); when(userRepository.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(admin)); when(auditRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), any(org.springframework.data.domain.Pageable.class))).thenReturn(Page.empty()); when(auditRepository.countBySuccessful(true)).thenReturn(2L); when(auditRepository.countBySuccessful(false)).thenReturn(1L);
        String token = jwtService.generateAccessToken(admin);
        mockMvc.perform(get("/api/v1/admin/login-audit").param("page", "0").param("size", "20").header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andExpect(jsonPath("$.content").isArray()).andExpect(jsonPath("$.password").doesNotExist()).andExpect(jsonPath("$.token").doesNotExist());
        mockMvc.perform(get("/api/v1/admin/login-audit/summary").header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andExpect(jsonPath("$.totalLoginAttempts").value(3));
    }
    private User user(String email, RoleName roleName) { User user = new User("Test", "User", email, "hash", UserStatus.ACTIVE); user.addRole(new Role(roleName)); return user; }
}
