package com.example.expenseclaims.dto;

import jakarta.validation.constraints.Size;

/**
 * Optional JSON body for POST /api/claims/{id}/approve. The comment may be left out.
 *
 * There is intentionally no field for the approver: the approver is always the logged-in manager.
 */
public record ApproveClaimRequest(

        // Matches approval_comments.comment_text VARCHAR(1000).
        @Size(max = 1000, message = "comment must be at most 1000 characters")
        String comment
) {
}
