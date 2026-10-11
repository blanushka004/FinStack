package com.finstack.transaction;

public class InactiveAccountException extends RuntimeException {

    public InactiveAccountException() {
        super("Inactive accounts cannot receive new transactions");
    }
}
