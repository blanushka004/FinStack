package com.finstack.transaction;

public class InvalidTransactionAmountException extends RuntimeException {

    public InvalidTransactionAmountException() {
        super("Transaction amount must be greater than zero");
    }
}
