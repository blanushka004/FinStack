package com.finstack.category;

public class CategoryAlreadyExistsException extends RuntimeException {

    public CategoryAlreadyExistsException() {
        super("A category with this name and type already exists");
    }
}
