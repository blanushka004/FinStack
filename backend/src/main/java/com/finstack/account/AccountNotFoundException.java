package com.finstack.account;

public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException() {
        super("Financial account was not found");
    }
}
