package com.finstack.transaction;

import com.finstack.auth.ApiError;
import com.finstack.category.CategoryAlreadyExistsException;
import com.finstack.category.CategoryNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class TransactionExceptionHandler {

    @ExceptionHandler(TransactionNotFoundException.class)
    public ResponseEntity<ApiError> handleTransactionNotFound() {
        return error(HttpStatus.NOT_FOUND, "TRANSACTION_NOT_FOUND", "Transaction was not found");
    }

    @ExceptionHandler(CategoryNotFoundException.class)
    public ResponseEntity<ApiError> handleCategoryNotFound() {
        return error(HttpStatus.NOT_FOUND, "CATEGORY_NOT_FOUND", "Transaction category was not found");
    }

    @ExceptionHandler(CategoryAlreadyExistsException.class)
    public ResponseEntity<ApiError> handleCategoryAlreadyExists() {
        return error(HttpStatus.CONFLICT, "CATEGORY_ALREADY_EXISTS", "A category with this name and type already exists");
    }

    @ExceptionHandler(InactiveAccountException.class)
    public ResponseEntity<ApiError> handleInactiveAccount() {
        return error(HttpStatus.BAD_REQUEST, "INACTIVE_ACCOUNT", "Inactive accounts cannot receive new transactions");
    }

    @ExceptionHandler(InsufficientFundsException.class)
    public ResponseEntity<ApiError> handleInsufficientFunds(InsufficientFundsException exception) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, "INSUFFICIENT_FUNDS", exception.getMessage());
    }

    @ExceptionHandler({CategoryTypeMismatchException.class, InvalidTransactionAmountException.class})
    public ResponseEntity<ApiError> handleInvalidTransaction() {
        return error(HttpStatus.BAD_REQUEST, "INVALID_TRANSACTION", "Transaction details are invalid");
    }

    @ExceptionHandler(InvalidTransactionFilterException.class)
    public ResponseEntity<ApiError> handleInvalidFilter() {
        return error(HttpStatus.BAD_REQUEST, "INVALID_FILTER", "Transaction filters are invalid");
    }

    @ExceptionHandler(InvalidIdempotencyKeyException.class)
    public ResponseEntity<ApiError> handleInvalidIdempotencyKey() {
        return error(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY", "Idempotency-Key must be 1–128 letters, digits, dots, underscores, colons or hyphens");
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    public ResponseEntity<ApiError> handleIdempotencyKeyConflict() {
        return error(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_CONFLICT", "This Idempotency-Key was already used for a different transaction request");
    }

    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message) {
        return ResponseEntity.status(status).body(new ApiError(code, message, Map.of()));
    }
}
