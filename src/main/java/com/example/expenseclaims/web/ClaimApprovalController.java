package com.example.expenseclaims.web;

import com.example.expenseclaims.dto.ApproveClaimRequest;
import com.example.expenseclaims.dto.ClaimDecisionResponse;
import com.example.expenseclaims.dto.RejectClaimRequest;
import com.example.expenseclaims.security.CurrentEmployeeProvider;
import com.example.expenseclaims.service.ClaimApprovalService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manager decisions on expense claims. Only the MANAGER role reaches these methods (see SecurityConfig);
 * the deciding manager is always the logged-in user, never an id from the request.
 */
@RestController
@RequestMapping("/api/claims/{claimId}")
public class ClaimApprovalController {

    private final ClaimApprovalService claimApprovalService;
    private final CurrentEmployeeProvider currentEmployeeProvider;

    public ClaimApprovalController(ClaimApprovalService claimApprovalService,
                                   CurrentEmployeeProvider currentEmployeeProvider) {
        this.claimApprovalService = claimApprovalService;
        this.currentEmployeeProvider = currentEmployeeProvider;
    }

    // The body is optional: {"comment": "..."} or nothing at all.
    @PostMapping("/approve")
    public ClaimDecisionResponse approve(@PathVariable Long claimId,
                                         @Valid @RequestBody(required = false) ApproveClaimRequest request) {
        String comment = request == null ? null : request.comment();
        return claimApprovalService.approveClaim(currentEmployeeProvider.currentEmployeeId(), claimId, comment);
    }

    @PostMapping("/reject")
    public ClaimDecisionResponse reject(@PathVariable Long claimId,
                                        @Valid @RequestBody RejectClaimRequest request) {
        return claimApprovalService.rejectClaim(currentEmployeeProvider.currentEmployeeId(), claimId, request.comment());
    }
}
