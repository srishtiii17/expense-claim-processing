package com.example.expenseclaims.service;

import com.example.expenseclaims.dto.EmployeeSummaryResponse;
import com.example.expenseclaims.dto.LoginRequest;
import com.example.expenseclaims.dto.LoginResponse;
import com.example.expenseclaims.entity.Employee;
import com.example.expenseclaims.repository.EmployeeRepository;
import com.example.expenseclaims.security.JwtTokenService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Checks an email and password against the employees table and issues a JWT.
 *
 * Employees are not created here (there is no registration endpoint); their BCrypt password hash
 * must already be stored in employees.password_hash.
 */
@Service
@Transactional(readOnly = true)
public class AuthService {

    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;

    // A hash of a random value nobody knows. Checking against it when the email is unknown makes a
    // failed login take about as long for a wrong email as for a wrong password, so response times
    // do not reveal which email addresses exist.
    private final String unknownUserHash;

    public AuthService(EmployeeRepository employeeRepository, PasswordEncoder passwordEncoder,
                       JwtTokenService jwtTokenService) {
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.unknownUserHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    public LoginResponse login(LoginRequest request) {
        Optional<Employee> employee = employeeRepository.findByEmail(request.email());

        String storedHash = employee.map(Employee::getPasswordHash).orElse(unknownUserHash);
        boolean passwordMatches = passwordEncoder.matches(request.password(), storedHash);
        if (employee.isEmpty() || !passwordMatches) {
            throw new InvalidCredentialsException();
        }

        Employee loggedIn = employee.get();
        JwtTokenService.IssuedToken token = jwtTokenService.issueToken(loggedIn);
        long expiresIn = Duration.between(Instant.now(), token.expiresAt()).toSeconds();

        return new LoginResponse(token.value(), "Bearer", expiresIn, token.expiresAt(),
                EmployeeSummaryResponse.fromEntity(loggedIn), loggedIn.getRole());
    }
}
