package com.example.expenseclaims.dto;

import com.example.expenseclaims.entity.ExpenseCategory;

import java.math.BigDecimal;

/**
 * One line of the monthly report: a category and its total approved amount.
 */
public record CategoryTotalResponse(ExpenseCategory category, BigDecimal total) {
}
