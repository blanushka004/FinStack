package com.finstack.transaction;

public class InvalidTransactionFilterException extends RuntimeException {

    public InvalidTransactionFilterException() {
        super("Transaction filter range is invalid");
    }
}
