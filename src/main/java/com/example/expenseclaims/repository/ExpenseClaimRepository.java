package com.example.expenseclaims.repository;

import com.example.expenseclaims.entity.ExpenseClaim;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Database access for expense claims.
 * Queries for pending approvals and reports are added in later checkpoints.
 */
public interface ExpenseClaimRepository extends JpaRepository<ExpenseClaim, Long> {

    // "My claims", newest first. Uses the index on expense_claims(employee_id).
    // The id breaks ties when two claims have the same createdAt.
    List<ExpenseClaim> findByEmployeeIdOrderByCreatedAtDescIdDesc(Long employeeId);
}
