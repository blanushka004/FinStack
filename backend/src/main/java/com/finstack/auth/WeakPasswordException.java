package com.finstack.auth;
public class WeakPasswordException extends RuntimeException { public WeakPasswordException() { super(PasswordPolicy.MESSAGE); } }
