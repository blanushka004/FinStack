package com.finstack.transaction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finstack.transaction.dto.TransactionCreateRequest;
import com.finstack.transaction.dto.TransactionResponse;
import com.finstack.user.User;
import com.finstack.user.UserRepository;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Service
public class IdempotentTransactionCreationService {
    private final TransactionService transactions;
    private final TransactionCreateIdempotencyRepository records;
    private final UserRepository users;
    private final ObjectMapper objectMapper;

    public IdempotentTransactionCreationService(TransactionService transactions,
                                                TransactionCreateIdempotencyRepository records,
                                                UserRepository users, ObjectMapper objectMapper) {
        this.transactions = transactions;
        this.records = records;
        this.users = users;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public TransactionResponse create(TransactionCreateRequest request, String key) {
        if (key == null) return transactions.create(request);
        if (key.isBlank() || key.length() > 128 || !key.matches("[A-Za-z0-9._:-]+")) {
            throw new InvalidIdempotencyKeyException();
        }
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthenticationCredentialsNotFoundException("Authentication is required");
        }
        User user = users.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Authenticated user is unavailable"));

        // Lock before lookup: concurrent retries for this user serialize, then observe the committed record.
        User lockedUser = users.findByIdForUpdate(user.getId()).orElseThrow();
        String requestHash = hash(request);
        var existing = records.findByUserIdAndIdempotencyKey(lockedUser.getId(), key);
        if (existing.isPresent()) {
            if (!MessageDigest.isEqual(requestHash.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                    existing.get().getRequestHash().getBytes(java.nio.charset.StandardCharsets.US_ASCII))) {
                throw new IdempotencyKeyConflictException();
            }
            return readResponse(existing.get().getResponseJson());
        }
        TransactionResponse response = transactions.create(request);
        records.save(new TransactionCreateIdempotency(lockedUser, key, requestHash, writeResponse(response)));
        return response;
    }

    private String hash(TransactionCreateRequest request) {
        try {
            byte[] payload = objectMapper.writeValueAsBytes(request);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(payload));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Could not fingerprint transaction request", exception);
        }
    }

    private String writeResponse(TransactionResponse response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not store transaction response", exception);
        }
    }

    private TransactionResponse readResponse(String json) {
        try {
            return objectMapper.readValue(json, TransactionResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read transaction response", exception);
        }
    }
}
