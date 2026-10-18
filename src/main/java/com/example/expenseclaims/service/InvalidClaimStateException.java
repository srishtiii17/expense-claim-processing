package com.example.expenseclaims.service;

import com.example.expenseclaims.entity.ClaimStatus;

/**
 * Thrown when a claim is not in a status that allows the requested change, for example approving
 * a claim that is already APPROVED or REJECTED. Mapped to HTTP 409 Conflict.
 */
public class InvalidClaimStateException extends RuntimeException {

    public InvalidClaimStateException(Long claimId, ClaimStatus currentStatus) {
        super("Expense claim " + claimId + " is already " + currentStatus
                + "; only SUBMITTED claims can be approved or rejected");
    }
}
