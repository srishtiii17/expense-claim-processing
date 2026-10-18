package com.example.expenseclaims.repository;

import com.example.expenseclaims.dto.ClaimSearchCriteria;
import com.example.expenseclaims.entity.ClaimStatus;
import com.example.expenseclaims.entity.Employee;
import com.example.expenseclaims.entity.ExpenseClaim;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * Reusable query conditions ("Specifications") for expense claims. Spring Data turns each one into
 * part of a SQL WHERE clause, and they can be combined with and(...), so one repository method
 * handles every combination of filters.
 *
 * All values are passed to the database as bind parameters, never pasted into SQL text.
 */
public final class ClaimSpecifications {

    private ClaimSpecifications() {
    }

    /**
     * Which claims the caller may see, based on their role in the database:
     * EMPLOYEE - only their own; MANAGER - everyone's in their own department; FINANCE - all claims.
     */
    public static Specification<ExpenseClaim> visibleTo(Employee caller) {
        return (root, query, cb) -> switch (caller.getRole()) {
            case EMPLOYEE -> cb.equal(root.get("employee").get("id"), caller.getId());
            case MANAGER -> cb.equal(root.get("employee").get("department").get("id"), caller.getDepartment().getId());
            case FINANCE -> cb.conjunction(); // always true: finance sees every department (read-only)
        };
    }

    /**
     * The optional filters from GET /api/claims. A null field adds no condition.
     */
    public static Specification<ExpenseClaim> matching(ClaimSearchCriteria criteria) {
        return (root, query, cb) -> {
            List<Predicate> conditions = new ArrayList<>();
            if (criteria.status() != null) {
                conditions.add(cb.equal(root.get("status"), criteria.status()));
            }
            if (criteria.category() != null) {
                conditions.add(cb.equal(root.get("category"), criteria.category()));
            }
            if (criteria.minAmount() != null) {
                conditions.add(cb.greaterThanOrEqualTo(root.get("amount"), criteria.minAmount()));
            }
            if (criteria.maxAmount() != null) {
                conditions.add(cb.lessThanOrEqualTo(root.get("amount"), criteria.maxAmount()));
            }
            if (criteria.startDate() != null) {
                conditions.add(cb.greaterThanOrEqualTo(root.get("expenseDate"), criteria.startDate()));
            }
            if (criteria.endDate() != null) {
                conditions.add(cb.lessThanOrEqualTo(root.get("expenseDate"), criteria.endDate()));
            }
            return cb.and(conditions.toArray(new Predicate[0]));
        };
    }

    /**
     * Claims a manager can act on: SUBMITTED, from the manager's department, and not the manager's own
     * (nobody may decide their own claim, assumption A11).
     */
    public static Specification<ExpenseClaim> pendingApprovalFor(Employee manager) {
        return (root, query, cb) -> cb.and(
                cb.equal(root.get("status"), ClaimStatus.SUBMITTED),
                cb.equal(root.get("employee").get("department").get("id"), manager.getDepartment().getId()),
                cb.notEqual(root.get("employee").get("id"), manager.getId()));
    }

    public static Specification<ExpenseClaim> hasId(Long claimId) {
        return (root, query, cb) -> cb.equal(root.get("id"), claimId);
    }
}
