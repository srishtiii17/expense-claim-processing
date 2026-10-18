package com.example.expenseclaims.security;

/**
 * A CurrentEmployeeProvider for tests only: each test says which employee is "logged in".
 *
 * It lives in src/test, so it is never part of the application that runs in production.
 * It is deliberately not a @Component; tests register it through TestAuthenticationConfiguration.
 */
public class TestCurrentEmployeeProvider implements CurrentEmployeeProvider {

    private Long employeeId;

    public void signInAs(Long employeeId) {
        this.employeeId = employeeId;
    }

    public void signOut() {
        this.employeeId = null;
    }

    @Override
    public Long currentEmployeeId() {
        if (employeeId == null) {
            // Same behaviour as production when nobody is signed in.
            throw new AuthenticationRequiredException("Authentication is required.");
        }
        return employeeId;
    }
}
