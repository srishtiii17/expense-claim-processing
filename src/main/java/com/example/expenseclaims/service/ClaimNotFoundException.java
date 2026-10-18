package com.example.expenseclaims.service;

/**
 * Thrown when a claim does not exist, or exists but the caller may not see it.
 * Both cases give the same answer, so callers cannot discover other people's claim ids.
 */
public class ClaimNotFoundException extends RuntimeException {

    public ClaimNotFoundException(Long claimId) {
        super("Expense claim " + claimId + " was not found");
    }
}
