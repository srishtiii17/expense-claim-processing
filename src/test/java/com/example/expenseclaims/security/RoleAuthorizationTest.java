package com.example.expenseclaims.security;

import com.example.expenseclaims.entity.Department;
import com.example.expenseclaims.entity.Employee;
import com.example.expenseclaims.entity.Role;
import com.example.expenseclaims.repository.DepartmentRepository;
import com.example.expenseclaims.repository.EmployeeRepository;
import com.example.expenseclaims.repository.ExpenseClaimRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The role matrix for the endpoints that exist so far:
 *
 *                       EMPLOYEE   MANAGER   FINANCE
 *   POST /api/claims      201        403       403
 *   GET  /api/claims/mine 200        200       200   (own claims only)
 *
 * Roles are always read from the database, never from the token or the request.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RoleAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private JwtEncoder jwtEncoder;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private ExpenseClaimRepository expenseClaimRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    private Employee employee;
    private Employee manager;
    private Employee finance;

    @BeforeEach
    void createEmployees() {
        Department sales = departmentRepository.save(new Department("Sales"));
        employee = employeeRepository.save(new Employee("Ravi Kumar", "ravi@example.com", "hash", Role.EMPLOYEE, sales));
        manager = employeeRepository.save(new Employee("Karan Shah", "karan@example.com", "hash", Role.MANAGER, sales));
        finance = employeeRepository.save(new Employee("Fatima Ali", "fatima@example.com", "hash", Role.FINANCE, sales));
    }

    @Test
    void employeeCanSubmitAClaim() throws Exception {
        submitClaim(tokenFor(employee), validClaimJson())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    @Test
    void managerCannotSubmitAClaim() throws Exception {
        assertForbiddenAndNothingSaved(tokenFor(manager));
    }

    @Test
    void financeCannotSubmitAClaim() throws Exception {
        assertForbiddenAndNothingSaved(tokenFor(finance));
    }

    @Test
    void managerWithAnInvalidBodyStillGets403Not400() throws Exception {
        // The role check happens before the body is read, so validation cannot be used to get past it.
        submitClaim(tokenFor(manager), "{\"amount\": -1}")
                .andExpect(status().isForbidden());
    }

    @Test
    void everyRoleCanListTheirOwnClaims() throws Exception {
        for (Employee anyone : new Employee[]{employee, manager, finance}) {
            mockMvc.perform(get("/api/claims/mine").header(HttpHeaders.AUTHORIZATION, tokenFor(anyone)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    @Test
    void unknownRouteWithAValidTokenIs404() throws Exception {
        mockMvc.perform(get("/api/no-such-route").header(HttpHeaders.AUTHORIZATION, tokenFor(employee)))
                .andExpect(status().isNotFound());
    }

    @Test
    void aRoleClaimAddedToTheTokenIsIgnored() throws Exception {
        // Even a correctly signed token that says "role: EMPLOYEE" does not make a manager an employee,
        // because the role comes from the database.
        Instant now = Instant.now();
        String managerClaimingEmployee = "Bearer " + JwtTestTokens.sign(jwtEncoder, claims -> claims
                .issuer(JwtTestTokens.ISSUER).subject(String.valueOf(manager.getId()))
                .issuedAt(now).expiresAt(now.plusSeconds(600))
                .claim("role", "EMPLOYEE").claim("scope", "EMPLOYEE").claim("authorities", "ROLE_EMPLOYEE"));

        assertForbiddenAndNothingSaved(managerClaimingEmployee);
    }

    @Test
    void aRoleSentInTheRequestIsIgnored() throws Exception {
        String json = """
                {"category": "MEALS", "amount": 20.00, "expenseDate": "%s", "role": "EMPLOYEE"}
                """.formatted(LocalDate.now().minusDays(1));

        submitClaim(tokenFor(manager), json).andExpect(status().isForbidden());
    }

    @Test
    void roleChangesInTheDatabaseApplyToExistingTokens() throws Exception {
        String token = tokenFor(employee);

        // Promote Ravi to MANAGER directly in the database after his token was issued.
        jdbcTemplate.update("UPDATE employees SET role = 'MANAGER' WHERE id = ?", employee.getId());
        entityManager.clear();

        submitClaim(token, validClaimJson()).andExpect(status().isForbidden());
    }

    private void assertForbiddenAndNothingSaved(String token) throws Exception {
        long claimsBefore = expenseClaimRepository.count();

        submitClaim(token, validClaimJson())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Not allowed"))
                .andExpect(jsonPath("$.detail").value("You do not have permission to perform this action."));

        assertThat(expenseClaimRepository.count()).isEqualTo(claimsBefore);
    }

    private ResultActions submitClaim(String token, String json) throws Exception {
        return mockMvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON).content(json)
                .header(HttpHeaders.AUTHORIZATION, token));
    }

    private String tokenFor(Employee someone) {
        return "Bearer " + jwtTokenService.issueToken(someone).value();
    }

    private static String validClaimJson() {
        return """
                {"category": "MEALS", "amount": 20.00, "expenseDate": "%s", "description": "Lunch"}
                """.formatted(LocalDate.now().minusDays(1));
    }
}
