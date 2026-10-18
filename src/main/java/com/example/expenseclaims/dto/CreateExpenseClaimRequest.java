package com.example.expenseclaims.dto;

import com.example.expenseclaims.entity.ExpenseCategory;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * The JSON body a client sends to submit a new claim.
 *
 * There is intentionally no employeeId or status field: the owner will come from the
 * logged-in user's token (Checkpoint 3) and every new claim starts as SUBMITTED.
 *
 * The validation rules mirror the database columns in V1__create_initial_tables.sql, so a bad
 * request is rejected with a clear message instead of reaching a database constraint.
 */
public record CreateExpenseClaimRequest(

        @NotNull(message = "category is required")
        ExpenseCategory category,

        // NUMERIC(12,2): at most 12 digits in total, 2 of them after the decimal point,
        // so at most 10 before it. The database also has CHECK (amount > 0).
        @NotNull(message = "amount is required")
        @Positive(message = "amount must be greater than zero")
        @Digits(integer = 10, fraction = 2,
                message = "amount must have at most 10 digits before and 2 after the decimal point")
        BigDecimal amount,

        // "Today" is the current date in the server's default time zone (assumption A4;
        // the company time zone is an open mentor question).
        @NotNull(message = "expenseDate is required")
        @PastOrPresent(message = "expenseDate cannot be in the future")
        LocalDate expenseDate,

        // Optional (the column allows NULL), but limited to the VARCHAR(500) column size.
        @Size(max = 500, message = "description must be at most 500 characters")
        String description
) {
}
