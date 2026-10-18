package com.example.expenseclaims.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * GET /api/reports/monthly-expenses-by-category.
 *
 * @param month              the reported month, for example "2026-10"
 * @param periodStart        first day included (the 1st of the month)
 * @param periodEndExclusive first day NOT included (the 1st of the next month)
 * @param scope              "ORGANIZATION" (finance) or "DEPARTMENT" (manager)
 * @param departmentName     the manager's department; null for the organisation-wide report
 * @param totals             one entry per category that has approved claims, alphabetical; empty if none
 * @param grandTotal         sum of all totals; 0.00 if there are none
 */
public record MonthlyCategoryReportResponse(
        String month,
        LocalDate periodStart,
        LocalDate periodEndExclusive,
        String scope,
        String departmentName,
        List<CategoryTotalResponse> totals,
        BigDecimal grandTotal
) {
}
