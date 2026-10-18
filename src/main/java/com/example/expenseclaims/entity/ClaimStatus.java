package com.example.expenseclaims.entity;

/**
 * The lifecycle of an expense claim (single-stage manager approval, requirement C1):
 *
 * SUBMITTED -> APPROVED
 * SUBMITTED -> REJECTED
 *
 * The manager's decision is final; there is no second (finance) approval stage.
 * The rules that enforce these transitions are added in Checkpoint 4.
 */
public enum ClaimStatus {
    SUBMITTED,
    APPROVED,
    REJECTED
}
