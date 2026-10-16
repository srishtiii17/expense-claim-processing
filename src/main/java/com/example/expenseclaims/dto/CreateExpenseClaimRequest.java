package com.example.expenseclaims.dto;

import com.example.expenseclaims.entity.ExpenseCategory;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The JSON body a client sends to submit a new claim.
 *
 * There is intentionally no employeeId or status field: the owner will come from the
 * logged-in user's token (Checkpoint 3) and every new claim starts as SUBMITTED.
 * Validation annotations (amount > 0, date not in the future, category required)
 * are added in Checkpoint 2.
 */
public record CreateExpenseClaimRequest(
        ExpenseCategory category,
        BigDecimal amount,
        LocalDate expenseDate,
        String description
) {
}
