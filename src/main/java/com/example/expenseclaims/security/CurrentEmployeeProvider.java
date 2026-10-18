package com.example.expenseclaims.security;

/**
 * Answers "which employee is making this request?".
 *
 * This is the single place where the application learns the caller's identity. The id must come
 * from a trusted source (in Checkpoint 3: a verified JWT), never from the request body, a query
 * parameter or a header the caller can set freely.
 */
public interface CurrentEmployeeProvider {

    /**
     * @return the id of the authenticated employee
     * @throws AuthenticationRequiredException if no authenticated employee is available
     */
    Long currentEmployeeId();
}
