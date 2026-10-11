package com.finstack.transaction;

import com.finstack.transaction.dto.TransactionCreateRequest;
import com.finstack.security.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TransactionController.class)
@AutoConfigureMockMvc(addFilters = false)
class TransactionIdempotencyControllerTest {
    @Autowired private MockMvc mockMvc;
    @MockitoBean private TransactionService transactions;
    @MockitoBean private IdempotentTransactionCreationService idempotentCreation;
    @MockitoBean private JwtAuthenticationFilter jwtAuthenticationFilter;

    private static final String BODY = """
            {"accountId":"00000000-0000-0000-0000-000000000001","transactionType":"INCOME",
             "amount":25.00,"transactionTime":"2026-10-05T10:00:00Z","status":"COMPLETED"}
            """;

    @Test
    void legacyClientWithoutKeyKeepsExistingCreatePath() throws Exception {
        mockMvc.perform(post("/api/v1/transactions").contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());
        verify(transactions).create(any(TransactionCreateRequest.class));
    }

    @Test
    void keyedClientUsesIdempotentCreatePath() throws Exception {
        mockMvc.perform(post("/api/v1/transactions").header("Idempotency-Key", "qa-request-1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());
        verify(idempotentCreation).create(any(TransactionCreateRequest.class), eq("qa-request-1"));
    }

    @Test
    void reusedKeyWithDifferentRequestHasSafeConflictResponse() throws Exception {
        when(idempotentCreation.create(any(TransactionCreateRequest.class), eq("qa-request-1")))
                .thenThrow(new IdempotencyKeyConflictException());
        mockMvc.perform(post("/api/v1/transactions").header("Idempotency-Key", "qa-request-1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_CONFLICT"));
    }
}
