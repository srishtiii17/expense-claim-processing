package com.example.expenseclaims.web;

import com.example.expenseclaims.dto.CreateExpenseClaimRequest;
import com.example.expenseclaims.dto.ExpenseClaimResponse;
import com.example.expenseclaims.security.CurrentEmployeeProvider;
import com.example.expenseclaims.service.ExpenseClaimService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

/**
 * HTTP endpoints for expense claims. This class only translates between HTTP and the service:
 * it finds out who is calling, passes the request on, and picks the status code.
 * Errors are turned into responses by ApiExceptionHandler.
 */
@RestController
@RequestMapping("/api/claims")
public class ExpenseClaimController {

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
