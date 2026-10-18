package com.example.expenseclaims.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * JWT settings, read from application.properties (prefix "app.jwt").
 *
 * The checks below run while the application starts. A missing or too-short secret stops startup
 * with a clear message, instead of starting with weak or no token protection.
 *
 * @param secret HMAC-SHA256 signing secret (environment variable JWT_SECRET), at least 32 bytes
 * @param expiry how long an issued token stays valid (environment variable JWT_EXPIRY, default 1h)
 * @param issuer value of the "iss" claim; tokens with any other issuer are rejected
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(String secret, Duration expiry, String issuer) {

    // HS256 needs a key of at least 256 bits (32 bytes).
    static final int MINIMUM_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "The JWT signing secret is not configured. Set the JWT_SECRET environment variable "
                            + "to a random value of at least " + MINIMUM_SECRET_BYTES + " characters.");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MINIMUM_SECRET_BYTES) {
            // The message never includes the secret itself.
            throw new IllegalStateException(
                    "The JWT signing secret (JWT_SECRET) is too short: it must be at least "
                            + MINIMUM_SECRET_BYTES + " bytes.");
        }
        if (expiry == null || expiry.isZero() || expiry.isNegative()) {
            throw new IllegalStateException("The JWT expiry (JWT_EXPIRY) must be a positive duration, for example 1h.");
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("The JWT issuer (app.jwt.issuer) must not be blank.");
        }
    }

    // Keep the secret out of logs and error messages if this object is ever printed.
    @Override
    public String toString() {
        return "JwtProperties[secret=<hidden>, expiry=" + expiry + ", issuer=" + issuer + "]";
    }
}
