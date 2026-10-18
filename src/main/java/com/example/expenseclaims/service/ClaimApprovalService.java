package com.example.expenseclaims.service;

import com.example.expenseclaims.dto.ApprovalCommentResponse;
import com.example.expenseclaims.dto.ClaimDecisionResponse;
import com.example.expenseclaims.dto.ExpenseClaimResponse;
import com.example.expenseclaims.entity.ApprovalComment;
import com.example.expenseclaims.entity.ClaimStatus;
import com.example.expenseclaims.entity.CommentAction;
import com.example.expenseclaims.entity.Employee;
import com.example.expenseclaims.entity.ExpenseClaim;
import com.example.expenseclaims.entity.Role;
import com.example.expenseclaims.repository.ApprovalCommentRepository;
import com.example.expenseclaims.repository.EmployeeRepository;
import com.example.expenseclaims.repository.ExpenseClaimRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * The manager approval workflow (single stage, requirement C1):
 *
 *   SUBMITTED -> APPROVED   (comment optional)
 *   SUBMITTED -> REJECTED   (comment required)
 *
 * APPROVED and REJECTED are final. Each decision is stored twice, in one transaction: as the claim's
 * new status, and as an ApprovalComment (APPROVE or REJECT) written by the deciding manager. Either
 * both are saved or neither is.
 *
 * Concurrency: ExpenseClaim has a @Version column. If two decisions on the same claim run at the same
 * time, both may read it as SUBMITTED, but only the first UPDATE ... WHERE version = n succeeds. The
 * second fails with an optimistic locking error, its transaction rolls back (including its comment),
 * and the caller gets HTTP 409.
 */
@Service
public class ClaimApprovalService {

    private final ExpenseClaimRepository expenseClaimRepository;
    private final ApprovalCommentRepository approvalCommentRepository;
    private final EmployeeRepository employeeRepository;

    public ClaimApprovalService(ExpenseClaimRepository expenseClaimRepository,
                                ApprovalCommentRepository approvalCommentRepository,
                                EmployeeRepository employeeRepository) {
        this.expenseClaimRepository = expenseClaimRepository;
        this.approvalCommentRepository = approvalCommentRepository;
        this.employeeRepository = employeeRepository;
    }

    @Transactional
    public ClaimDecisionResponse approveClaim(Long managerId, Long claimId, String comment) {
        return decide(managerId, claimId, ClaimStatus.APPROVED, CommentAction.APPROVE, normalise(comment));
    }

    @Transactional
    public ClaimDecisionResponse rejectClaim(Long managerId, Long claimId, String reason) {
        String normalisedReason = normalise(reason);
        // The request DTO already requires a reason; this protects callers that skip HTTP validation.
        if (normalisedReason == null) {
            throw new IllegalArgumentException("A reason is required to reject a claim");
        }
        return decide(managerId, claimId, ClaimStatus.REJECTED, CommentAction.REJECT, normalisedReason);
    }

    private ClaimDecisionResponse decide(Long managerId, Long claimId, ClaimStatus newStatus,
                                         CommentAction action, String comment) {
        Employee manager = employeeRepository.findById(managerId)
                .orElseThrow(() -> new EmployeeNotFoundException(managerId));
        if (manager.getRole() != Role.MANAGER) {
            throw new ClaimDecisionNotAllowedException("Only managers can approve or reject expense claims");
        }

        ExpenseClaim claim = expenseClaimRepository.findById(claimId)
                .orElseThrow(() -> new ClaimNotFoundException(claimId));

        // A manager may only decide claims from their own department. Claims from other departments
        // answer 404, the same as a missing claim, so managers cannot discover other departments' claims.
        Long claimDepartmentId = claim.getEmployee().getDepartment().getId();
        if (!Objects.equals(claimDepartmentId, manager.getDepartment().getId())) {
            throw new ClaimNotFoundException(claimId);
        }

        // Managers do not submit claims (C3), but a claim can still belong to someone who became a
        // manager later. Nobody may decide their own claim.
        if (Objects.equals(claim.getEmployee().getId(), manager.getId())) {
            throw new ClaimDecisionNotAllowedException("You cannot approve or reject your own expense claim");
        }

        if (claim.getStatus() != ClaimStatus.SUBMITTED) {
            throw new InvalidClaimStateException(claimId, claim.getStatus());
        }

        // Flush the status change first: this runs UPDATE ... WHERE id = ? AND version = ?, so a
        // concurrent decision is detected here, before the audit comment is written.
        claim.setStatus(newStatus);
        ExpenseClaim decided = expenseClaimRepository.saveAndFlush(claim);

        ApprovalComment auditComment = approvalCommentRepository.saveAndFlush(
                new ApprovalComment(decided, manager, action, comment));

        return new ClaimDecisionResponse(ExpenseClaimResponse.fromEntity(decided),
                ApprovalCommentResponse.fromEntity(auditComment));
    }

    // Trims surrounding spaces; an empty or blank comment is stored as "no comment" (NULL).
    private static String normalise(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }
        return comment.strip();
    }
}
