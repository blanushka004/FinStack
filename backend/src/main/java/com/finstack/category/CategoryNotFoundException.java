package com.finstack.category;

public class CategoryNotFoundException extends RuntimeException {

    public CategoryNotFoundException() {
        super("Transaction category was not found");
    }
}
