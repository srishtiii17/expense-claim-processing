package com.example.expenseclaims.dto;

/**
 * What approve and reject return: the claim in its new status, and the audit comment that records
 * who decided, what they decided and why.
 */
public record ClaimDecisionResponse(
        ExpenseClaimResponse claim,
        ApprovalCommentResponse decision
) {
}
