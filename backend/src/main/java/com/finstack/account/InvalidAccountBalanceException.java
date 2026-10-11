package com.finstack.account;

public class InvalidAccountBalanceException extends RuntimeException {
    public InvalidAccountBalanceException(String message) {
        super(message);
    }
}
