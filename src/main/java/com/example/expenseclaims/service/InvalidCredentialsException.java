package com.example.expenseclaims.service;

/**
 * Thrown when a login fails. Deliberately the same for an unknown email and a wrong password,
 * so the response never reveals which email addresses have an account.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password.");
    }
}
