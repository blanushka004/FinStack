package com.finstack.account;

public class InvalidCreditLimitException extends RuntimeException {

    public InvalidCreditLimitException(String message) {
        super(message);
    }
}
