package com.finstack.auth;

import com.finstack.security.*;
import com.finstack.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtService.class, JwtAuthenticationFilter.class, CustomUserDetailsService.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {"jwt.secret=test-secret-that-is-at-least-thirty-two-bytes-long",
        "jwt.access-token-expiration-ms=3600000"})
class PasswordResetControllerSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean AuthService auth;
    @MockitoBean PasswordResetService reset;
    @MockitoBean UserRepository users;

    @Test void requestIsPublicAndReturnsIdenticalResponseForUnknownAndKnownEmails() throws Exception {
        String body = "{\"email\":\"person@example.com\"}";
        String expected = mvc.perform(post("/api/v1/auth/password-reset/request").contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message", not(containsString("person@example.com"))))
                .andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/v1/auth/password-reset/request").contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(content().string(expected));
        verify(reset, times(2)).request(eq("person@example.com"), any());
    }

    @Test void confirmsPubliclyButRejectsInvalidTokensAndWeakPayloadsSafely() throws Exception {
        doThrow(new InvalidPasswordResetTokenException()).when(reset).confirm("bad", "Replacement2!");
        mvc.perform(post("/api/v1/auth/password-reset/confirm").contentType("application/json")
                .content("{\"token\":\"bad\",\"password\":\"Replacement2!\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_RESET_TOKEN"));
        mvc.perform(post("/api/v1/auth/password-reset/confirm").contentType("application/json")
                .content("{\"token\":\"bad\",\"password\":\"weak\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/password-reset/request").contentType("application/json")
                .content("{\"email\":\"not-an-email\"}"))
                .andExpect(status().isBadRequest());
    }
}
