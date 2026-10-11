package com.finstack.transaction;

public class CategoryTypeMismatchException extends RuntimeException {

    public CategoryTypeMismatchException() {
        super("Transaction category type must match transaction type");
    }
}
