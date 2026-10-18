package com.example.expenseclaims.web;

import com.example.expenseclaims.dto.ClaimSearchCriteria;
import com.example.expenseclaims.dto.CreateExpenseClaimRequest;
import com.example.expenseclaims.dto.ExpenseClaimResponse;
import com.example.expenseclaims.dto.PageResponse;
import com.example.expenseclaims.entity.ClaimStatus;
import com.example.expenseclaims.entity.ExpenseCategory;
import com.example.expenseclaims.security.CurrentEmployeeProvider;
import com.example.expenseclaims.service.ExpenseClaimService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;

/**
 * HTTP endpoints for expense claims. This class only translates between HTTP and the service:
 * it finds out who is calling, passes the request on, and picks the status code.
 * Errors are turned into responses by ApiExceptionHandler.
 */
@RestController
@RequestMapping("/api/claims")
public class ExpenseClaimController {

    // Upper limit for the "size" parameter of paged endpoints.
    static final int MAX_PAGE_SIZE = 100;

    private final ExpenseClaimService expenseClaimService;
    private final CurrentEmployeeProvider currentEmployeeProvider;

    public ExpenseClaimController(ExpenseClaimService expenseClaimService,
                                  CurrentEmployeeProvider currentEmployeeProvider) {
        this.expenseClaimService = expenseClaimService;
        this.currentEmployeeProvider = currentEmployeeProvider;
    }

    // @Valid runs the validation annotations on CreateExpenseClaimRequest; a failure becomes a 400.
    @PostMapping
    public ResponseEntity<ExpenseClaimResponse> submitClaim(@Valid @RequestBody CreateExpenseClaimRequest request) {
        ExpenseClaimResponse created =
                expenseClaimService.submitClaim(currentEmployeeProvider.currentEmployeeId(), request);

        // 201 Created with a Location header pointing at the new claim, for example /api/claims/42.
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    /**
     * Claims the caller may see (employee: own; manager: own department; finance: all), with optional
     * filters, newest first. Example: /api/claims?status=SUBMITTED&category=TRAVEL&minAmount=100
     * Invalid values (status=PENDING, startDate=2026-13-01, minAmount=abc) are answered with 400 by
     * Spring's type conversion; contradictory ranges by ClaimSearchCriteria.
     */
    @GetMapping
    public PageResponse<ExpenseClaimResponse> searchClaims(
            @RequestParam(required = false) ClaimStatus status,
            @RequestParam(required = false) ExpenseCategory category,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        ClaimSearchCriteria criteria =
                new ClaimSearchCriteria(status, category, minAmount, maxAmount, startDate, endDate);
        return expenseClaimService.searchClaims(currentEmployeeProvider.currentEmployeeId(), criteria, page, size);
    }

    // MANAGER only (SecurityConfig): SUBMITTED claims of the manager's own department, oldest first.
    @GetMapping("/pending-approvals")
    public PageResponse<ExpenseClaimResponse> getPendingApprovals(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(MAX_PAGE_SIZE) int size) {
        return expenseClaimService.getPendingApprovals(currentEmployeeProvider.currentEmployeeId(), page, size);
    }

    // The caller's own claims, newest first.
    @GetMapping("/mine")
    public List<ExpenseClaimResponse> getMyClaims() {
        return expenseClaimService.getClaimsOf(currentEmployeeProvider.currentEmployeeId());
    }

    @GetMapping("/{claimId}")
    public ExpenseClaimResponse getClaim(@PathVariable Long claimId) {
        return expenseClaimService.getClaim(currentEmployeeProvider.currentEmployeeId(), claimId);
    }
}
