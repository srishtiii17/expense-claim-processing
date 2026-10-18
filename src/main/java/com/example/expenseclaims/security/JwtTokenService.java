package com.example.expenseclaims.security;

import com.example.expenseclaims.entity.Employee;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Creates signed login tokens (JWTs).
 *
 * A token contains only: iss (issuer), sub (the employee id), iat (issued at) and exp (expires at).
 * The role is deliberately NOT in the token: it is read from the database on every request
 * (see EmployeeJwtAuthenticationConverter), so a role change applies immediately and a token
 * can never carry a role the database does not give.
 */
@Service
public class JwtTokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    public JwtTokenService(JwtEncoder jwtEncoder, JwtProperties jwtProperties) {
        this.jwtEncoder = jwtEncoder;
        this.jwtProperties = jwtProperties;
    }

    public IssuedToken issueToken(Employee employee) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(jwtProperties.expiry());

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(jwtProperties.issuer())
                .subject(String.valueOf(employee.getId()))
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new IssuedToken(token, expiresAt);
    }

    /**
     * A freshly signed token and the moment it stops being valid.
     */
    public record IssuedToken(String value, Instant expiresAt) {

        // Never print the token itself.
        @Override
        public String toString() {
            return "IssuedToken[value=<hidden>, expiresAt=" + expiresAt + "]";
        }
    }
}
