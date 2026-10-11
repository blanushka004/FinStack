package com.finstack.account;

import com.finstack.auth.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class FinancialAccountExceptionHandler {

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ApiError> handleAccountNotFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("ACCOUNT_NOT_FOUND", "Financial account was not found", Map.of()));
    }

    @ExceptionHandler(InvalidCreditLimitException.class)
    public ResponseEntity<ApiError> handleInvalidCreditLimit() {
        return ResponseEntity.badRequest()
                .body(new ApiError("INVALID_CREDIT_LIMIT", "Credit limit is invalid for this account type", Map.of()));
    }

    @ExceptionHandler(InvalidAccountBalanceException.class)
    public ResponseEntity<ApiError> handleInvalidAccountBalance(InvalidAccountBalanceException exception) {
        return ResponseEntity.badRequest()
                .body(new ApiError("INVALID_ACCOUNT_BALANCE", exception.getMessage(), Map.of()));
    }
}
