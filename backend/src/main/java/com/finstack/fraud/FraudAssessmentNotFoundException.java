package com.finstack.fraud;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.UUID;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class FraudAssessmentNotFoundException extends RuntimeException {
    public FraudAssessmentNotFoundException(UUID transactionId) {
        super("Fraud assessment not found for transaction " + transactionId);
    }
}
