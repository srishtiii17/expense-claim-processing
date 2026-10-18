package com.example.expenseclaims.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

/**
 * The production CurrentEmployeeProvider: reads the employee id from the verified JWT that Spring
 * Security placed in the security context for this request (see EmployeeJwtAuthenticationConverter).
 *
 * It never looks at the request body, query parameters or other headers.
 */
@Component
public class JwtCurrentEmployeeProvider implements CurrentEmployeeProvider {

    @Override
    public Long currentEmployeeId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication && jwtAuthentication.isAuthenticated()) {
            return Long.valueOf(jwtAuthentication.getName());
        }
        throw new AuthenticationRequiredException("Authentication is required.");
    }
}
