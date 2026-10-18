package com.example.expenseclaims.repository;

import com.example.expenseclaims.entity.ExpenseCategory;

import java.math.BigDecimal;

/**
 * One row of the monthly report query: a category and the database-computed SUM of its amounts.
 */
public record CategoryTotal(ExpenseCategory category, BigDecimal total) {
}
