package com.finstack.fraud;

import com.finstack.fraud.dto.FraudAssessmentResponse;
import com.finstack.fraud.dto.FraudSummaryResponse;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FraudController.class)
@Import({SecurityConfig.class, JwtService.class, JwtAuthenticationFilter.class, CustomUserDetailsService.class, RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class})
@TestPropertySource(properties = {"jwt.secret=test-secret-that-is-at-least-thirty-two-bytes-long", "jwt.access-token-expiration-ms=3600000"})
class FraudControllerSecurityTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @MockitoBean private FraudQueryService fraudQueries;
    @MockitoBean private UserRepository users;

    @Test
    void authenticatedRoleUserCanReadAssessmentPageWithoutExposingAnotherUsersData() throws Exception {
        User user = new User("Ada", "Lovelace", "ada@example.com", "hash", UserStatus.ACTIVE);
        user.addRole(new Role(RoleName.ROLE_USER));
        when(users.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));
        FraudAssessmentResponse assessment = new FraudAssessmentResponse(UUID.randomUUID(), UUID.randomUUID(), new BigDecimal("425.50"),
                "Coffee Shop", "FOOD", 45, RiskLevel.MEDIUM, false, List.of("LARGE_TRANSACTION"), OffsetDateTime.now());
        when(fraudQueries.list(any())).thenReturn(new PageImpl<>(List.of(assessment)));

        mockMvc.perform(get("/api/v1/fraud/assessments").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateAccessToken(user)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].transactionId").value(assessment.transactionId().toString()))
                .andExpect(jsonPath("$.content[0].amount").value(425.50))
                .andExpect(jsonPath("$.content[0].categoryName").value("FOOD"));
    }

    @Test
    void anonymousFraudAssessmentRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/fraud/assessments"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void authenticatedRoleUserCanReadTransactionAssessmentAndSummary() throws Exception {
        User user = new User("Ada", "Lovelace", "ada@example.com", "hash", UserStatus.ACTIVE);
        user.addRole(new Role(RoleName.ROLE_USER));
        when(users.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));
        UUID transactionId = UUID.randomUUID();
        FraudAssessmentResponse assessment = new FraudAssessmentResponse(UUID.randomUUID(), transactionId, new BigDecimal("425.50"),
                "Coffee Shop", "FOOD", 45, RiskLevel.MEDIUM, false, List.of("LARGE_TRANSACTION"), OffsetDateTime.now());
        when(fraudQueries.byTransaction(transactionId)).thenReturn(assessment);
        when(fraudQueries.summary()).thenReturn(new FraudSummaryResponse(2, 3, 1, 1));
        String token = jwtService.generateAccessToken(user);

        mockMvc.perform(get("/api/v1/fraud/transactions/{transactionId}", transactionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value(transactionId.toString()))
                .andExpect(jsonPath("$.amount").value(425.50));

        mockMvc.perform(get("/api/v1/fraud/summary").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mediumRisk").value(3))
                .andExpect(jsonPath("$.suspicious").value(1));
    }

    @Test
    void foreignOrMissingAssessmentReturnsSafeNotFoundInsteadOfUnauthorized() throws Exception {
        User user = new User("Ada", "Lovelace", "ada@example.com", "hash", UserStatus.ACTIVE);
        user.addRole(new Role(RoleName.ROLE_USER));
        when(users.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(user));
        UUID transactionId = UUID.randomUUID();
        when(fraudQueries.byTransaction(transactionId))
                .thenThrow(new FraudAssessmentNotFoundException(transactionId));

        mockMvc.perform(get("/api/v1/fraud/transactions/{transactionId}", transactionId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtService.generateAccessToken(user)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FRAUD_ASSESSMENT_NOT_FOUND"));
    }
}
