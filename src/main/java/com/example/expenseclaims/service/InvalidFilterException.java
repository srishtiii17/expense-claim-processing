package com.example.expenseclaims.service;

/**
 * Thrown when search filters contradict each other, for example minAmount greater than maxAmount.
 * Mapped to HTTP 400.
 */
public class InvalidFilterException extends RuntimeException {

    public InvalidFilterException(String message) {
        super(message);
    }
}
