package com.example.expenseclaims.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The JSON body for POST /api/auth/login.
 */
public record LoginRequest(

        @NotBlank(message = "email is required")
        @Size(max = 255, message = "email must be at most 255 characters")
        String email,

        // BCrypt only looks at the first 72 bytes of a password, so longer input is refused up front.
        @NotBlank(message = "password is required")
        @Size(max = 72, message = "password must be at most 72 characters")
        String password
) {

    // Never print the password, for example if this object ends up in a log message.
    @Override
    public String toString() {
        return "LoginRequest[email=" + email + ", password=<hidden>]";
    }
}
