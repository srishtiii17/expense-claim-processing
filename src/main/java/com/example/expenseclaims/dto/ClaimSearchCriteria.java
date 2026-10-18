package com.example.expenseclaims.dto;

import com.example.expenseclaims.entity.ClaimStatus;
import com.example.expenseclaims.entity.ExpenseCategory;
import com.example.expenseclaims.service.InvalidFilterException;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Optional filters for GET /api/claims. Every field may be null, meaning "no filter on this field".
 * Ranges are inclusive at both ends: minAmount <= amount <= maxAmount and startDate <= expenseDate <= endDate.
 *
 * There is deliberately no employee or department filter: who can see which claims is decided by the
 * caller's role (ClaimSpecifications.visibleTo), never by the request.
 */
public record ClaimSearchCriteria(
        ClaimStatus status,
        ExpenseCategory category,
        BigDecimal minAmount,
        BigDecimal maxAmount,
        LocalDate startDate,
        LocalDate endDate
) {

    public ClaimSearchCriteria {
        if (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0) {
            throw new InvalidFilterException("minAmount must not be greater than maxAmount");
        }
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new InvalidFilterException("startDate must not be after endDate");
        }
    }

    public static ClaimSearchCriteria none() {
        return new ClaimSearchCriteria(null, null, null, null, null, null);
    }
}
