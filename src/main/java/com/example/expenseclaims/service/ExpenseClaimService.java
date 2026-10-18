package com.example.expenseclaims.service;

import com.example.expenseclaims.dto.ClaimSearchCriteria;
import com.example.expenseclaims.dto.CreateExpenseClaimRequest;
import com.example.expenseclaims.dto.ExpenseClaimResponse;
import com.example.expenseclaims.dto.PageResponse;
import com.example.expenseclaims.entity.Employee;
import com.example.expenseclaims.entity.ExpenseClaim;
import com.example.expenseclaims.entity.Role;
import com.example.expenseclaims.repository.ClaimSpecifications;
import com.example.expenseclaims.repository.EmployeeRepository;
import com.example.expenseclaims.repository.ExpenseClaimRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for submitting and reading expense claims.
 *
 * Every method receives the id of the employee the request acts for. The service does not know
 * where that id comes from; the controller gets it from CurrentEmployeeProvider. That way the JWT
 * login in Checkpoint 3 only replaces the provider, not this class.
 *
 * DTOs are built inside the transaction, because related entities are loaded lazily and
 * open-in-view is disabled.
 */
@Service
@Transactional(readOnly = true)
public class ExpenseClaimService {

    private final ExpenseClaimRepository expenseClaimRepository;
    private final EmployeeRepository employeeRepository;

    public ExpenseClaimService(ExpenseClaimRepository expenseClaimRepository,
                               EmployeeRepository employeeRepository) {
        this.expenseClaimRepository = expenseClaimRepository;
        this.employeeRepository = employeeRepository;
    }

    /**
     * Saves a new claim owned by the given employee. The ExpenseClaim constructor sets the status to SUBMITTED.
     */
    @Transactional
    public ExpenseClaimResponse submitClaim(Long employeeId, CreateExpenseClaimRequest request) {
        Employee employee = findEmployee(employeeId);
        if (employee.getRole() != Role.EMPLOYEE) {
            throw new ClaimSubmissionNotAllowedException(employee.getRole());
        }

        // Validation (@Digits) guarantees at most 2 decimal places, so setScale(2) never rounds.
        // It makes the response show 125.50 exactly as NUMERIC(12,2) stores it, not 125.5.
        ExpenseClaim claim = new ExpenseClaim(employee, request.category(), request.amount().setScale(2),
                request.expenseDate(), request.description());

        return ExpenseClaimResponse.fromEntity(expenseClaimRepository.save(claim));
    }

    /**
     * Returns one claim if the caller may see it (same rule as searchClaims: employees their own,
     * managers their department's, finance all). A claim the caller may not see answers 404, exactly
     * like a missing one, so claim ids cannot be probed (A10).
     */
    public ExpenseClaimResponse getClaim(Long employeeId, Long claimId) {
        Employee caller = findEmployee(employeeId);
        ExpenseClaim claim = expenseClaimRepository
                .findOne(ClaimSpecifications.visibleTo(caller).and(ClaimSpecifications.hasId(claimId)))
                .orElseThrow(() -> new ClaimNotFoundException(claimId));
        return ExpenseClaimResponse.fromEntity(claim);
    }

    /**
     * GET /api/claims: the claims the caller may see, narrowed by optional filters, newest first.
     * The visibility rule always applies; filters can only narrow it further.
     */
    public PageResponse<ExpenseClaimResponse> searchClaims(Long employeeId, ClaimSearchCriteria criteria,
                                                           int page, int size) {
        Employee caller = findEmployee(employeeId);
        Specification<ExpenseClaim> specification =
                ClaimSpecifications.visibleTo(caller).and(ClaimSpecifications.matching(criteria));
        PageRequest pageRequest = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return PageResponse.from(expenseClaimRepository.findAll(specification, pageRequest),
                ExpenseClaimResponse::fromEntity);
    }

    /**
     * GET /api/claims/pending-approvals: SUBMITTED claims from the manager's own department that the
     * manager may decide (their own claims are left out), oldest first so the queue is worked in order.
     */
    public PageResponse<ExpenseClaimResponse> getPendingApprovals(Long managerId, int page, int size) {
        Employee manager = findEmployee(managerId);
        // The URL rule in SecurityConfig already refuses other roles; this protects non-HTTP callers.
        if (manager.getRole() != Role.MANAGER) {
            throw new AccessDeniedException("Only managers have a pending-approval list");
        }
        PageRequest pageRequest = PageRequest.of(page, size,
                Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id")));
        return PageResponse.from(
                expenseClaimRepository.findAll(ClaimSpecifications.pendingApprovalFor(manager), pageRequest),
                ExpenseClaimResponse::fromEntity);
    }

    /**
     * Returns all claims owned by the given employee, newest first.
     */
    public List<ExpenseClaimResponse> getClaimsOf(Long employeeId) {
        findEmployee(employeeId);
        return expenseClaimRepository.findByEmployeeIdOrderByCreatedAtDescIdDesc(employeeId).stream()
                .map(ExpenseClaimResponse::fromEntity)
                .toList();
    }

    private Employee findEmployee(Long employeeId) {
        return employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
    }
}
