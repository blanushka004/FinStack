package com.finstack.dashboard;

import com.finstack.dashboard.dto.DashboardAnalyticsResponse;
import com.finstack.role.Role;
import com.finstack.role.RoleName;
import com.finstack.security.CustomUserDetailsService;
import com.finstack.security.JwtAuthenticationFilter;
import com.finstack.security.JwtService;
import com.finstack.security.RestAccessDeniedHandler;
import com.finstack.security.RestAuthenticationEntryPoint;
import com.finstack.security.SecurityConfig;
import com.finstack.user.User;
import com.finstack.user.UserRepository;
import com.finstack.user.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardController.class)
@Import({SecurityConfig.class, JwtService.class, JwtAuthenticationFilter.class, CustomUserDetailsService.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
@TestPropertySource(properties = {"jwt.secret=test-secret-that-is-at-least-thirty-two-bytes-long", "jwt.access-token-expiration-ms=3600000"})
class DashboardControllerSecurityTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @MockitoBean private DashboardService dashboardService;
    @MockitoBean private UserRepository users;

    @Test
    void authenticatedUserCanReadTheirAnalytics() throws Exception {
        User user = new User("Ada", "Lovelace", "ada@example.com", "hash", UserStatus.ACTIVE);
        user.addRole(new Role(RoleName.ROLE_USER));
        when(users.findByEmailIgnoreCase(user.getEmail())).thenReturn(Optional.of(user));
        when(dashboardService.analytics()).thenReturn(new DashboardAnalyticsResponse(
                List.of(new DashboardAnalyticsResponse.MonthlyCashFlow(
                        LocalDate.of(2026, 9, 1), new BigDecimal("900.00"), new BigDecimal("400.00"), new BigDecimal("500.00")
                )),
                List.of(new DashboardAnalyticsResponse.CategorySpending(null, "Uncategorized", new BigDecimal("400.00")))
        ));

        mockMvc.perform(get("/api/v1/dashboard/analytics")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateAccessToken(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cashFlowTrend[0].income").value(900.00))
                .andExpect(jsonPath("$.categorySpending[0].categoryName").value("Uncategorized"));
    }

    @Test
    void anonymousUserCannotReadAnalytics() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/analytics"))
                .andExpect(status().isUnauthorized());
    }
}
