package com.example.expenseclaims.web;

import com.example.expenseclaims.entity.Department;
import com.example.expenseclaims.entity.Employee;
import com.example.expenseclaims.entity.Role;
import com.example.expenseclaims.repository.DepartmentRepository;
import com.example.expenseclaims.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests POST /api/auth/login against real employee rows in the PostgreSQL test database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerTest {

    private static final String PASSWORD = "correct horse battery staple";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtDecoder jwtDecoder;

    private Employee ravi;
    private String raviPasswordHash;

    @BeforeEach
    void createEmployee() {
        Department sales = departmentRepository.save(new Department("Sales"));
        raviPasswordHash = passwordEncoder.encode(PASSWORD);
        ravi = employeeRepository.save(
                new Employee("Ravi Kumar", "ravi@example.com", raviPasswordHash, Role.EMPLOYEE, sales));
    }

    @Test
    void successfulLoginReturnsASignedTokenForThatEmployee() throws Exception {
        String body = login("ravi@example.com", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresAt").exists())
                .andExpect(jsonPath("$.employee.id").value(ravi.getId()))
                .andExpect(jsonPath("$.employee.fullName").value("Ravi Kumar"))
                .andExpect(jsonPath("$.employee.departmentName").value("Sales"))
                .andExpect(jsonPath("$.role").value("EMPLOYEE"))
                .andReturn().getResponse().getContentAsString();

        String token = com.jayway.jsonpath.JsonPath.read(body, "$.accessToken");
        Integer expiresIn = com.jayway.jsonpath.JsonPath.read(body, "$.expiresIn");

        // The token is verified by the application's own decoder and contains exactly what we expect.
        Jwt jwt = jwtDecoder.decode(token);
        assertThat(jwt.getSubject()).isEqualTo(String.valueOf(ravi.getId()));
        assertThat(jwt.getClaimAsString("iss")).isEqualTo("expense-claims");
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofHours(1));
        assertThat(expiresIn).isBetween(3590, 3600);
        assertThat(jwt.getClaims()).doesNotContainKeys("role", "email", "password");

        // And it works on a protected endpoint.
        mockMvc.perform(get("/api/claims/mine").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void loginResponseNeverContainsThePasswordOrItsHash() throws Exception {
        String body = login("ravi@example.com", PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employee.email").doesNotExist())
                .andExpect(jsonPath("$.employee.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(PASSWORD, raviPasswordHash, "passwordHash", "ravi@example.com");
    }

    @Test
    void passwordsAreStoredAsBcryptHashes() {
        assertThat(raviPasswordHash).startsWith("$2").hasSize(60).doesNotContain(PASSWORD);
        assertThat(employeeRepository.findById(ravi.getId()).orElseThrow().getPasswordHash())
                .isEqualTo(raviPasswordHash);
    }

    @Test
    void wrongPasswordAndUnknownEmailGetTheSameGeneric401() throws Exception {
        String wrongPassword = login("ravi@example.com", "wrong password")
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Authentication failed"))
                .andExpect(jsonPath("$.detail").value("Invalid email or password."))
                .andExpect(jsonPath("$.accessToken").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        String unknownEmail = login("nobody@example.com", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

        // Identical bodies: the caller cannot tell which part was wrong.
        assertThat(unknownEmail).isEqualTo(wrongPassword);
    }

    @Test
    void plainTextPasswordStoredInTheDatabaseIsNeverAccepted() throws Exception {
        // If someone stored a password without hashing it, logging in with that text must still fail,
        // because only BCrypt hashes are compared.
        Department sales = departmentRepository.findAll().getFirst();
        employeeRepository.save(new Employee("Plain Text", "plain@example.com", "plain-password", Role.EMPLOYEE, sales));

        login("plain@example.com", "plain-password")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid email or password."));
    }

    @Test
    void blankCredentialsReturn400() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"\", \"password\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").value("email is required"))
                .andExpect(jsonPath("$.errors.password").value("password is required"));
    }

    @Test
    void passwordLongerThan72CharactersReturns400() throws Exception {
        login("ravi@example.com", "a".repeat(73))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value("password must be at most 72 characters"));
    }

    @Test
    void loginIsPublicButOtherAuthRoutesAreNot() throws Exception {
        mockMvc.perform(get("/api/auth/login"))
                .andExpect(status().isUnauthorized());
    }

    private ResultActions login(String email, String password) throws Exception {
        String json = """
                {"email": "%s", "password": "%s"}
                """.formatted(email, password);
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
