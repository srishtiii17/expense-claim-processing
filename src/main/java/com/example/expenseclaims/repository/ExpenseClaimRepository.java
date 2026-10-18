package com.example.expenseclaims.repository;

import com.example.expenseclaims.entity.ExpenseClaim;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * Database access for expense claims.
 *
 * JpaSpecificationExecutor adds findAll(Specification, Pageable), used with ClaimSpecifications for
 * searching and the pending-approval list.
 */
public interface ExpenseClaimRepository extends JpaRepository<ExpenseClaim, Long>, JpaSpecificationExecutor<ExpenseClaim> {

    // "My claims", newest first. Uses the index on expense_claims(employee_id).
    // The id breaks ties when two claims have the same createdAt.
    List<ExpenseClaim> findByEmployeeIdOrderByCreatedAtDescIdDesc(Long employeeId);

    /**
     * Same as the inherited method, but loads each claim's employee and department in the same SQL
     * query (JOIN). Without this, turning N claims into DTOs would run up to N extra queries
     * ("N+1 problem"), because those relations are LAZY.
     */
    @Override
    @EntityGraph(attributePaths = {"employee", "employee.department"})
    Page<ExpenseClaim> findAll(Specification<ExpenseClaim> specification, Pageable pageable);

    /**
     * Monthly report for the whole organisation: SUM of APPROVED amounts per category with
     * start <= expense_date < end. The GROUP BY and SUM run inside PostgreSQL; only one row per
     * category comes back. Dates are bind parameters.
     */
    @Query("""
            SELECT new com.example.expenseclaims.repository.CategoryTotal(c.category, SUM(c.amount))
            FROM ExpenseClaim c
            WHERE c.status = com.example.expenseclaims.entity.ClaimStatus.APPROVED
              AND c.expenseDate >= :start AND c.expenseDate < :end
            GROUP BY c.category
            ORDER BY c.category
            """)
    List<CategoryTotal> sumApprovedAmountsByCategory(@Param("start") LocalDate start, @Param("end") LocalDate end);

    /**
     * The same report limited to claims owned by employees of one department.
     */
    @Query("""
            SELECT new com.example.expenseclaims.repository.CategoryTotal(c.category, SUM(c.amount))
            FROM ExpenseClaim c
            WHERE c.status = com.example.expenseclaims.entity.ClaimStatus.APPROVED
              AND c.expenseDate >= :start AND c.expenseDate < :end
              AND c.employee.department.id = :departmentId
            GROUP BY c.category
            ORDER BY c.category
            """)
    List<CategoryTotal> sumApprovedAmountsByCategoryForDepartment(@Param("start") LocalDate start,
                                                                   @Param("end") LocalDate end,
                                                                   @Param("departmentId") Long departmentId);
}
