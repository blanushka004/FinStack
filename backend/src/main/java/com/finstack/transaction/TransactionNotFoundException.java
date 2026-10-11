package com.finstack.transaction;

public class TransactionNotFoundException extends RuntimeException {

    public TransactionNotFoundException() {
        super("Transaction was not found");
    }
}
