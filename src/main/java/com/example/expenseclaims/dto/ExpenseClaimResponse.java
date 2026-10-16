package com.example.expenseclaims.dto;

import com.example.expenseclaims.entity.ClaimStatus;
import com.example.expenseclaims.entity.ExpenseCategory;
import com.example.expenseclaims.entity.ExpenseClaim;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * What the API returns for an expense claim. Controllers return this instead of the
 * ExpenseClaim entity, so internal fields (such as the version number or the employee's
 * password hash) are never sent to clients.
 */
public record ExpenseClaimResponse(
        Long id,
        ExpenseCategory category,
        BigDecimal amount,
        LocalDate expenseDate,
        String description,
        ClaimStatus status,
        EmployeeSummaryResponse submittedBy,
        Instant createdAt,
        Instant updatedAt
) {

    public static ExpenseClaimResponse fromEntity(ExpenseClaim claim) {
        return new ExpenseClaimResponse(
                claim.getId(),
                claim.getCategory(),
                claim.getAmount(),
                claim.getExpenseDate(),
                claim.getDescription(),
                claim.getStatus(),
                EmployeeSummaryResponse.fromEntity(claim.getEmployee()),
                claim.getCreatedAt(),
                claim.getUpdatedAt()
        );
    }
}
