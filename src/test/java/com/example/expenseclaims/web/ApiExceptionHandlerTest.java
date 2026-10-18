package com.example.expenseclaims.web;

import com.example.expenseclaims.entity.ExpenseClaim;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A lost race is hard to produce through HTTP on demand, so the mapping of an optimistic locking
 * failure to a response is checked directly. ClaimApprovalTransactionTest proves the failure itself.
 */
class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void optimisticLockingFailureBecomesA409WithoutInternalDetails() {
        ObjectOptimisticLockingFailureException failure = new ObjectOptimisticLockingFailureException(
                ExpenseClaim.class, 42L, new IllegalStateException("Row was updated or deleted by another transaction"));

        ProblemDetail problem = handler.handleConcurrentUpdate(failure);

        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getTitle()).isEqualTo("Conflict");
        assertThat(problem.getDetail())
                .isEqualTo("The claim was changed by another request at the same time. Reload it and try again.")
                .doesNotContain("ExpenseClaim", "Row was updated", "42");
    }
}
