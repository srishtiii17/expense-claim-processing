package com.example.expenseclaims.web;

import com.example.expenseclaims.dto.MonthlyCategoryReportResponse;
import com.example.expenseclaims.security.CurrentEmployeeProvider;
import com.example.expenseclaims.service.ExpenseReportService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only reports. MANAGER and FINANCE only (see SecurityConfig); the scope (department or whole
 * organisation) follows from the caller's role, never from a request parameter.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ExpenseReportService expenseReportService;
    private final CurrentEmployeeProvider currentEmployeeProvider;

    public ReportController(ExpenseReportService expenseReportService,
                            CurrentEmployeeProvider currentEmployeeProvider) {
        this.expenseReportService = expenseReportService;
        this.currentEmployeeProvider = currentEmployeeProvider;
    }

    @GetMapping("/monthly-expenses-by-category")
    public MonthlyCategoryReportResponse monthlyExpensesByCategory() {
        return expenseReportService.monthlyTotalsByCategory(currentEmployeeProvider.currentEmployeeId());
    }
}
