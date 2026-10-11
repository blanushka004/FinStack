package com.finstack.fraud;

import com.finstack.auth.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class FraudExceptionHandler {
    @ExceptionHandler(FraudAssessmentNotFoundException.class)
    public ResponseEntity<ApiError> notFound() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError("FRAUD_ASSESSMENT_NOT_FOUND", "Fraud assessment was not found", Map.of()));
    }
}
