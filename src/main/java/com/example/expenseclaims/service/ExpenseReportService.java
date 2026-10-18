package com.example.expenseclaims.service;

import com.example.expenseclaims.dto.CategoryTotalResponse;
import com.example.expenseclaims.dto.MonthlyCategoryReportResponse;
import com.example.expenseclaims.entity.Employee;
import com.example.expenseclaims.repository.CategoryTotal;
import com.example.expenseclaims.repository.EmployeeRepository;
import com.example.expenseclaims.repository.ExpenseClaimRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

/**
 * Reports over approved claims (requirement C2: only APPROVED claims count).
 *
 * "This month" is the calendar month of today's date in the server's time zone (assumption A6,
 * open question 5). The Clock is injected so tests can fix "today".
 */
@Service
@Transactional(readOnly = true)
public class ExpenseReportService {

    private final ExpenseClaimRepository expenseClaimRepository;
    private final EmployeeRepository employeeRepository;
    private final Clock clock;

    public ExpenseReportService(ExpenseClaimRepository expenseClaimRepository,
                                EmployeeRepository employeeRepository, Clock clock) {
        this.expenseClaimRepository = expenseClaimRepository;
        this.employeeRepository = employeeRepository;
        this.clock = clock;
    }

    /**
     * Totals of APPROVED claims per category with expense_date in the current month.
     * FINANCE gets the whole organisation, MANAGER only their own department. EMPLOYEE is refused.
     */
    public MonthlyCategoryReportResponse monthlyTotalsByCategory(Long employeeId) {
        Employee caller = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new EmployeeNotFoundException(employeeId));

        YearMonth month = YearMonth.now(clock);
        LocalDate start = month.atDay(1);
        LocalDate endExclusive = month.plusMonths(1).atDay(1);

        List<CategoryTotal> rows;
        String scope;
        String departmentName;
        switch (caller.getRole()) {
            case FINANCE -> {
                rows = expenseClaimRepository.sumApprovedAmountsByCategory(start, endExclusive);
                scope = "ORGANIZATION";
                departmentName = null;
            }
            case MANAGER -> {
                rows = expenseClaimRepository.sumApprovedAmountsByCategoryForDepartment(
                        start, endExclusive, caller.getDepartment().getId());
                scope = "DEPARTMENT";
                departmentName = caller.getDepartment().getName();
            }
            // The URL rule in SecurityConfig already refuses employees; this protects non-HTTP callers.
            default -> throw new AccessDeniedException("Only managers and finance can view expense reports");
        }

        List<CategoryTotalResponse> totals = rows.stream()
                .map(row -> new CategoryTotalResponse(row.category(), row.total().setScale(2)))
                .toList();
        // Adding up at most six already-aggregated category totals; the per-claim sums ran in SQL.
        BigDecimal grandTotal = totals.stream()
                .map(CategoryTotalResponse::total)
                .reduce(new BigDecimal("0.00"), BigDecimal::add);

        return new MonthlyCategoryReportResponse(month.toString(), start, endExclusive, scope,
                departmentName, totals, grandTotal);
    }
}
