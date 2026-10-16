package com.example.expenseclaims.repository;

import com.example.expenseclaims.entity.ExpenseClaim;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Database access for expense claims.
 * Queries for filtering, pending approvals and reports are added in later checkpoints.
 */
public interface ExpenseClaimRepository extends JpaRepository<ExpenseClaim, Long> {
}
