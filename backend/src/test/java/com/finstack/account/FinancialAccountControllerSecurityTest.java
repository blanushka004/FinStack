package com.finstack.account;

import com.finstack.security.CustomUserDetailsService;
import com.finstack.security.JwtAuthenticationFilter;
import com.finstack.security.JwtService;
import com.finstack.security.RestAccessDeniedHandler;
import com.finstack.security.RestAuthenticationEntryPoint;
import com.finstack.security.SecurityConfig;
import com.finstack.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FinancialAccountController.class)
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
class FinancialAccountControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FinancialAccountService accountService;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void rejectsUnauthenticatedAccountAccess() throws Exception {
        mockMvc.perform(get("/api/v1/accounts"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
