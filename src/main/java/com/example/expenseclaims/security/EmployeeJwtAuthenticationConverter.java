package com.example.expenseclaims.security;

import com.example.expenseclaims.entity.Employee;
import com.example.expenseclaims.repository.EmployeeRepository;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Turns a token whose signature, issuer and expiry have already been verified into the logged-in user.
 *
 * The employee id comes from the "sub" claim. The role is loaded from the database, never taken from
 * the token, so editing the token cannot raise a role (editing it would also break the signature).
 * A token for an employee that no longer exists is rejected as unauthenticated (HTTP 401).
 */
@Component
public class EmployeeJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final EmployeeRepository employeeRepository;

    public EmployeeJwtAuthenticationConverter(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Long employeeId = parseEmployeeId(jwt.getSubject());
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new InvalidBearerTokenException("The token's employee does not exist"));

        // Spring Security role checks such as hasRole("EMPLOYEE") look for the authority "ROLE_EMPLOYEE".
        List<SimpleGrantedAuthority> authorities =
                List.of(new SimpleGrantedAuthority("ROLE_" + employee.getRole().name()));
        return new JwtAuthenticationToken(jwt, authorities, String.valueOf(employee.getId()));
    }

    private static Long parseEmployeeId(String subject) {
        try {
            return Long.valueOf(subject);
        } catch (NumberFormatException e) {
            throw new InvalidBearerTokenException("The token's subject is not an employee id");
        }
    }
}
