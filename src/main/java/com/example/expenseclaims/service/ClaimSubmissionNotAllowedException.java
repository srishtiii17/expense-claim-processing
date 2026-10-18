package com.example.expenseclaims.service;

import com.example.expenseclaims.entity.Role;

/**
 * Thrown when someone who is not allowed to submit claims tries to (requirement C3:
 * managers do not submit their own claims, so only the EMPLOYEE role may submit).
 */
public class ClaimSubmissionNotAllowedException extends RuntimeException {

    public ClaimSubmissionNotAllowedException(Role role) {
        super("Users with the " + role + " role cannot submit expense claims");
    }
}
