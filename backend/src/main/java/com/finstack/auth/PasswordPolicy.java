package com.finstack.auth;

import java.util.regex.Pattern;

public final class PasswordPolicy {
    public static final String MESSAGE = "Password must contain at least 8 characters, an uppercase letter, a lowercase letter, a number, and a special character";
    public static final Pattern PATTERN = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,72}$");

    private PasswordPolicy() { }

    public static boolean isValid(String password) {
        return password != null && PATTERN.matcher(password).matches();
    }
}
