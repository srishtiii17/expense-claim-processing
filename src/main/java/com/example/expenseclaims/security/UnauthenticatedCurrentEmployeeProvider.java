package com.example.expenseclaims.security;

import org.springframework.stereotype.Component;

/**
 * The production CurrentEmployeeProvider until JWT authentication exists (Checkpoint 3).
 *
 * There is no login yet, so no request has a trusted identity. Instead of guessing (for example by
 * trusting an employeeId sent by the caller), every request that needs an employee is refused with
 * HTTP 401. Checkpoint 3 replaces this class with one that reads the employee from the verified token.
 */
@Component
public class UnauthenticatedCurrentEmployeeProvider implements CurrentEmployeeProvider {

    @Override
    public Long currentEmployeeId() {
        throw new AuthenticationRequiredException(
                "Authentication is required. Login is not available yet (planned for Checkpoint 3).");
    }
}
