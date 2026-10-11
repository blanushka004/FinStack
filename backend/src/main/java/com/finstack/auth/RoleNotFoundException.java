package com.finstack.auth;

public class RoleNotFoundException extends RuntimeException {

    public RoleNotFoundException(String roleName) {
        super("Required role is not configured: " + roleName);
    }
}
