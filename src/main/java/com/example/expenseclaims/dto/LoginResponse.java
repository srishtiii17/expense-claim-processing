package com.example.expenseclaims.dto;

import com.example.expenseclaims.entity.Role;

import java.time.Instant;

/**
 * What POST /api/auth/login returns. The client sends accessToken back as
 * "Authorization: Bearer <accessToken>" on every later request.
 *
 * role is returned so a client can decide what to show; the server never trusts it in return,
 * because it reads the role from the database on every request.
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        Instant expiresAt,
        EmployeeSummaryResponse employee,
        Role role
) {

    // Never print the token.
    @Override
    public String toString() {
        return "LoginResponse[accessToken=<hidden>, tokenType=" + tokenType + ", expiresIn=" + expiresIn
                + ", expiresAt=" + expiresAt + ", employee=" + employee + ", role=" + role + "]";
    }
}
