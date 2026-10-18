package com.example.expenseclaims.security;

/**
 * Thrown when a request needs an authenticated employee but none is available. Mapped to HTTP 401.
 */
public class AuthenticationRequiredException extends RuntimeException {

    public AuthenticationRequiredException(String message) {
        super(message);
    }
}
