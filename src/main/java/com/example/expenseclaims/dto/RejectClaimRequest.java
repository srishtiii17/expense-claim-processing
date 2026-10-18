package com.example.expenseclaims.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * JSON body for POST /api/claims/{id}/reject. A rejection must explain why (assumption A5).
 *
 * There is intentionally no field for the approver: the approver is always the logged-in manager.
 */
public record RejectClaimRequest(

        @NotBlank(message = "comment is required when rejecting a claim")
        @Size(max = 1000, message = "comment must be at most 1000 characters")
        String comment
) {
}
