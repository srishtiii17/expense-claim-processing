package com.example.expenseclaims.service;

import com.example.expenseclaims.dto.CreateExpenseClaimRequest;
import com.example.expenseclaims.dto.ExpenseClaimResponse;
import com.example.expenseclaims.entity.Employee;
import com.example.expenseclaims.entity.ExpenseClaim;
import com.example.expenseclaims.entity.Role;
import com.example.expenseclaims.repository.EmployeeRepository;
import com.example.expenseclaims.repository.ExpenseClaimRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

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
     * Returns one claim if it belongs to the given employee.
     * Only the owner can see a claim here. Department views for managers and finance reporting are
     * planned for Checkpoint 5 (assumptions A2, A3, A10).
     */
    public ExpenseClaimResponse getClaim(Long employeeId, Long claimId) {
        ExpenseClaim claim = expenseClaimRepository.findById(claimId)
                .filter(found -> Objects.equals(found.getEmployee().getId(), employeeId))
                .orElseThrow(() -> new ClaimNotFoundException(claimId));
        return ExpenseClaimResponse.fromEntity(claim);
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
