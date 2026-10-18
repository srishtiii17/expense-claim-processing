package com.example.expenseclaims.service;

/**
 * Thrown when the caller may not approve or reject this claim: they are not a manager, or the claim
 * is their own. Mapped to HTTP 403.
 */
public class ClaimDecisionNotAllowedException extends RuntimeException {

    public ClaimDecisionNotAllowedException(String message) {
        super(message);
    }
}
