package com.example.expenseclaims.security;

import com.example.expenseclaims.entity.Department;
import com.example.expenseclaims.entity.Employee;
import com.example.expenseclaims.entity.ExpenseCategory;
import com.example.expenseclaims.entity.ExpenseClaim;
import com.example.expenseclaims.entity.Role;
import com.example.expenseclaims.repository.DepartmentRepository;
import com.example.expenseclaims.repository.EmployeeRepository;
import com.example.expenseclaims.repository.ExpenseClaimRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Every way a token can be wrong must end in the same 401, and a token can never be used
 * to act as a different employee.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class JwtAuthenticationTest {

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

    private Employee ravi;
    private Employee priya;

    @BeforeEach
    void createEmployees() {
        Department sales = departmentRepository.save(new Department("Sales"));
        ravi = employeeRepository.save(new Employee("Ravi Kumar", "ravi@example.com", "hash", Role.EMPLOYEE, sales));
        priya = employeeRepository.save(new Employee("Priya Nair", "priya@example.com", "hash", Role.EMPLOYEE, sales));
    }

    // ---- Accepted ----

    @Test
    void validTokenIsAccepted() throws Exception {
        callMyClaims("Bearer " + jwtTokenService.issueToken(ravi).value())
                .andExpect(status().isOk());
    }

    // ---- Rejected: all must be the same generic 401 ----

    @Test
    void missingTokenIsRejected() throws Exception {
        assertGeneric401(mockMvc.perform(get("/api/claims/mine")));
    }

    @Test
    void malformedTokenIsRejected() throws Exception {
        assertGeneric401(callMyClaims("Bearer not-a-jwt"));
        assertGeneric401(callMyClaims("Bearer aaa.bbb.ccc"));
        assertGeneric401(callMyClaims("Bearer "));
    }

    @Test
    void nonBearerAuthorizationIsRejected() throws Exception {
        assertGeneric401(callMyClaims("Basic cmF2aUBleGFtcGxlLmNvbTpwYXNzd29yZA=="));
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        // Expired two minutes ago, beyond the 60-second clock tolerance.
        Instant now = Instant.now();
        String expired = JwtTestTokens.sign(jwtEncoder, claims -> claims.issuer(JwtTestTokens.ISSUER)
                .subject(String.valueOf(ravi.getId())).issuedAt(now.minusSeconds(3720)).expiresAt(now.minusSeconds(120)));

        assertGeneric401(callMyClaims("Bearer " + expired));
    }

    @Test
    void tokenWithoutExpiryIsRejected() throws Exception {
        String noExpiry = JwtTestTokens.sign(jwtEncoder, claims -> claims.issuer(JwtTestTokens.ISSUER)
                .subject(String.valueOf(ravi.getId())).issuedAt(Instant.now()));

        assertGeneric401(callMyClaims("Bearer " + noExpiry));
    }

    @Test
    void tokenWithWrongIssuerIsRejected() throws Exception {
        Instant now = Instant.now();
        String otherIssuer = JwtTestTokens.sign(jwtEncoder, claims -> claims.issuer("someone-else")
                .subject(String.valueOf(ravi.getId())).issuedAt(now).expiresAt(now.plusSeconds(600)));

        assertGeneric401(callMyClaims("Bearer " + otherIssuer));
    }

    @Test
    void tokenWithNonNumericSubjectIsRejected() throws Exception {
        Instant now = Instant.now();
        String badSubject = JwtTestTokens.sign(jwtEncoder, claims -> claims.issuer(JwtTestTokens.ISSUER)
                .subject("ravi@example.com").issuedAt(now).expiresAt(now.plusSeconds(600)));

        assertGeneric401(callMyClaims("Bearer " + badSubject));
    }

    @Test
    void tokenSignedWithAnotherKeyIsRejected() throws Exception {
        assertGeneric401(callMyClaims("Bearer " + JwtTestTokens.signedWithUnknownKey(ravi.getId())));
    }

    @Test
    void unsignedTokenIsRejected() throws Exception {
        assertGeneric401(callMyClaims("Bearer " + JwtTestTokens.unsigned(ravi.getId())));
    }

    // ---- Impersonation ----

    @Test
    void changingTheSubjectOfAValidTokenBreaksTheSignature() throws Exception {
        // Ravi edits his own token so that it says he is Priya.
        String ravisToken = jwtTokenService.issueToken(ravi).value();
        String tampered = JwtTestTokens.withSubjectReplaced(ravisToken, ravi.getId(), priya.getId());

        assertGeneric401(callMyClaims("Bearer " + tampered));
    }

    @Test
    void aTokenOnlyGivesAccessToItsOwnEmployeesClaims() throws Exception {
        ExpenseClaim priyasClaim = expenseClaimRepository.save(new ExpenseClaim(priya, ExpenseCategory.TRAINING,
                new BigDecimal("99.00"), LocalDate.now().minusDays(1), "Priya's course"));
        String ravisToken = "Bearer " + jwtTokenService.issueToken(ravi).value();

        // Reading Priya's claim with Ravi's token: 404, as if it did not exist.
        mockMvc.perform(get("/api/claims/{id}", priyasClaim.getId()).header(HttpHeaders.AUTHORIZATION, ravisToken))
                .andExpect(status().isNotFound());

        // Ravi's list does not contain it, even if he names Priya in the query or a header.
        mockMvc.perform(get("/api/claims/mine").param("employeeId", String.valueOf(priya.getId()))
                        .header("X-Employee-Id", String.valueOf(priya.getId()))
                        .header(HttpHeaders.AUTHORIZATION, ravisToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // Submitting "as Priya" through the body still creates Ravi's claim.
        String json = """
                {"category": "MEALS", "amount": 12.00, "expenseDate": "%s", "employeeId": %d}
                """.formatted(LocalDate.now().minusDays(1), priya.getId());
        mockMvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON).content(json)
                        .header(HttpHeaders.AUTHORIZATION, ravisToken))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.submittedBy.id").value(ravi.getId()));
    }

    private ResultActions callMyClaims(String authorizationHeader) throws Exception {
        return mockMvc.perform(get("/api/claims/mine").header(HttpHeaders.AUTHORIZATION, authorizationHeader));
    }

    private static void assertGeneric401(ResultActions result) throws Exception {
        String body = result
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.title").value("Authentication required"))
                .andExpect(jsonPath("$.detail").value("A valid bearer token is required."))
                .andReturn().getResponse().getContentAsString();

        // The reason (expired, bad signature, ...) and library internals are never revealed.
        assertThat(body).doesNotContainIgnoringCase("exception")
                .doesNotContainIgnoringCase("expired")
                .doesNotContainIgnoringCase("signature")
                .doesNotContain("nimbus", "com.example", "trace");
    }
}
