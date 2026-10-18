package com.example.expenseclaims.web;

import com.example.expenseclaims.entity.ClaimStatus;
import com.example.expenseclaims.entity.Department;
import com.example.expenseclaims.entity.Employee;
import com.example.expenseclaims.entity.ExpenseCategory;
import com.example.expenseclaims.entity.ExpenseClaim;
import com.example.expenseclaims.entity.Role;
import com.example.expenseclaims.repository.DepartmentRepository;
import com.example.expenseclaims.repository.EmployeeRepository;
import com.example.expenseclaims.repository.ExpenseClaimRepository;
import com.example.expenseclaims.security.TestAuthenticationConfiguration;
import com.example.expenseclaims.security.TestCurrentEmployeeProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests the claim endpoints through the full stack: HTTP request -> controller -> validation ->
 * service -> repository -> PostgreSQL test database, and back as JSON.
 *
 * MockMvc sends requests without starting a real web server. @Transactional rolls back everything
 * each test writes, so tests do not affect each other. TestAuthenticationConfiguration replaces the
 * production identity provider (which always answers 401) with one each test controls.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(TestAuthenticationConfiguration.class)
class ExpenseClaimControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestCurrentEmployeeProvider currentEmployee;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private ExpenseClaimRepository expenseClaimRepository;

    @Autowired
    private EntityManager entityManager;

    private Employee ravi;
    private Employee priya;
    private Employee manager;

    @BeforeEach
    void createEmployees() {
        Department sales = departmentRepository.save(new Department("Sales"));
        ravi = employeeRepository.save(new Employee("Ravi Kumar", "ravi@example.com", "hash", Role.EMPLOYEE, sales));
        priya = employeeRepository.save(new Employee("Priya Nair", "priya@example.com", "hash", Role.EMPLOYEE, sales));
        manager = employeeRepository.save(new Employee("Karan Shah", "karan@example.com", "hash", Role.MANAGER, sales));
        currentEmployee.signInAs(ravi.getId());
    }

    @AfterEach
    void signOut() {
        currentEmployee.signOut();
    }

    // ---- Submitting a claim ----

    @Test
    void validClaimIsCreatedWithStatusSubmitted() throws Exception {
        mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(claimJson("TRAVEL", "125.5", LocalDate.now().minusDays(1), "Taxi to client office")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern("http://localhost/api/claims/\\d+")))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.category").value("TRAVEL"))
                .andExpect(jsonPath("$.amount").value(125.50))
                .andExpect(jsonPath("$.description").value("Taxi to client office"))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.submittedBy.id").value(ravi.getId()))
                .andExpect(jsonPath("$.submittedBy.fullName").value("Ravi Kumar"))
                .andExpect(jsonPath("$.submittedBy.departmentName").value("Sales"))
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    void submittedClaimIsSavedInTheDatabaseForTheCurrentEmployee() throws Exception {
        LocalDate expenseDate = LocalDate.now().minusDays(2);
        Long claimId = submitClaim(claimJson("MEALS", "42.00", expenseDate, "Team lunch"));

        // Write pending changes and empty the session, so the next read really comes from PostgreSQL.
        entityManager.flush();
        entityManager.clear();

        ExpenseClaim saved = expenseClaimRepository.findById(claimId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(ClaimStatus.SUBMITTED);
        assertThat(saved.getEmployee().getId()).isEqualTo(ravi.getId());
        assertThat(saved.getCategory()).isEqualTo(ExpenseCategory.MEALS);
        assertThat(saved.getAmount()).isEqualByComparingTo("42.00");
        assertThat(saved.getExpenseDate()).isEqualTo(expenseDate);
        assertThat(saved.getDescription()).isEqualTo("Team lunch");
    }

    @Test
    void ownerAndStatusInTheRequestBodyAreIgnored() throws Exception {
        // A caller must not be able to submit a claim for someone else or mark it approved.
        // CreateExpenseClaimRequest has no such fields, so these extra JSON properties are ignored.
        String json = """
                {"category": "OTHER", "amount": 10.00, "expenseDate": "%s",
                 "employeeId": %d, "status": "APPROVED"}
                """.formatted(LocalDate.now().minusDays(1), priya.getId());

        mockMvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.submittedBy.id").value(ravi.getId()))
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    @Test
    void responseDoesNotExposeSensitiveOrInternalFields() throws Exception {
        String body = mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(claimJson("OTHER", "10.00", LocalDate.now().minusDays(1), null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.version").doesNotExist())
                .andExpect(jsonPath("$.submittedBy.email").doesNotExist())
                .andExpect(jsonPath("$.submittedBy.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("ravi@example.com", "hash");
    }

    @Test
    void managerCannotSubmitAClaim() throws Exception {
        // Mentor clarification C3: managers do not submit claims.
        currentEmployee.signInAs(manager.getId());
        long claimsBefore = expenseClaimRepository.count();

        mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(claimJson("TRAVEL", "50.00", LocalDate.now().minusDays(1), "Taxi")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Not allowed"))
                .andExpect(jsonPath("$.detail").value("Users with the MANAGER role cannot submit expense claims"));

        assertThat(expenseClaimRepository.count()).isEqualTo(claimsBefore);
    }

    // ---- Invalid input ----

    @Test
    void invalidFieldsReturn400WithOneMessagePerField() throws Exception {
        long claimsBefore = expenseClaimRepository.count();
        String json = """
                {"amount": 0, "expenseDate": "%s", "description": "%s"}
                """.formatted(LocalDate.now().plusDays(2), "a".repeat(501));

        mockMvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.errors.category").value("category is required"))
                .andExpect(jsonPath("$.errors.amount").value("amount must be greater than zero"))
                .andExpect(jsonPath("$.errors.expenseDate").value("expenseDate cannot be in the future"))
                .andExpect(jsonPath("$.errors.description").value("description must be at most 500 characters"));

        assertThat(expenseClaimRepository.count()).isEqualTo(claimsBefore);
    }

    @Test
    void amountWithTooManyDecimalPlacesReturns400() throws Exception {
        mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(claimJson("MEALS", "12.345", LocalDate.now().minusDays(1), null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount")
                        .value("amount must have at most 10 digits before and 2 after the decimal point"));
    }

    @Test
    void unknownCategoryReturns400WithoutInternalDetails() throws Exception {
        String body = mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(claimJson("PIZZA", "10.00", LocalDate.now().minusDays(1), null)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn().getResponse().getContentAsString();

        assertNoInternalDetails(body);
    }

    @Test
    void malformedJsonReturns400WithoutInternalDetails() throws Exception {
        String body = mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"category\": \"MEALS\", \"amount\": "))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertNoInternalDetails(body);
    }

    // ---- Reading claims ----

    @Test
    void ownerCanReadTheirClaim() throws Exception {
        Long claimId = submitClaim(claimJson("ACCOMMODATION", "300.00", LocalDate.now().minusDays(5), "Hotel"));

        mockMvc.perform(get("/api/claims/{id}", claimId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(claimId))
                .andExpect(jsonPath("$.category").value("ACCOMMODATION"))
                .andExpect(jsonPath("$.amount").value(300.00))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.submittedBy.fullName").value("Ravi Kumar"));
    }

    @Test
    void myClaimsListsOnlyTheCurrentEmployeesClaimsNewestFirst() throws Exception {
        Long first = submitClaim(claimJson("MEALS", "20.00", LocalDate.now().minusDays(3), "Lunch"));
        Long second = submitClaim(claimJson("TRAVEL", "35.00", LocalDate.now().minusDays(2), "Train"));

        currentEmployee.signInAs(priya.getId());
        submitClaim(claimJson("TRAINING", "99.00", LocalDate.now().minusDays(1), "Priya's course"));

        currentEmployee.signInAs(ravi.getId());
        mockMvc.perform(get("/api/claims/mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(second))
                .andExpect(jsonPath("$[1].id").value(first));
    }

    @Test
    void myClaimsIsEmptyForAnEmployeeWithoutClaims() throws Exception {
        mockMvc.perform(get("/api/claims/mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ---- Nonexistent resources ----

    @Test
    void nonexistentClaimReturns404() throws Exception {
        mockMvc.perform(get("/api/claims/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Not found"))
                .andExpect(jsonPath("$.detail").value("Expense claim " + Long.MAX_VALUE + " was not found"));
    }

    @Test
    void anotherEmployeesClaimReturns404() throws Exception {
        // Answering 404 rather than 403 means callers cannot find out which claim ids exist.
        currentEmployee.signInAs(priya.getId());
        Long priyasClaim = submitClaim(claimJson("TRAINING", "99.00", LocalDate.now().minusDays(1), "Course"));

        currentEmployee.signInAs(ravi.getId());
        mockMvc.perform(get("/api/claims/{id}", priyasClaim))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Expense claim " + priyasClaim + " was not found"));
    }

    @Test
    void nonNumericClaimIdReturns400() throws Exception {
        String body = mockMvc.perform(get("/api/claims/abc"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        assertNoInternalDetails(body);
    }

    @Test
    void missingEmployeeReturns404AndSavesNothing() throws Exception {
        currentEmployee.signInAs(Long.MAX_VALUE);
        long claimsBefore = expenseClaimRepository.count();

        mockMvc.perform(post("/api/claims")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(claimJson("MEALS", "20.00", LocalDate.now().minusDays(1), "Lunch")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Employee " + Long.MAX_VALUE + " was not found"));

        mockMvc.perform(get("/api/claims/mine"))
                .andExpect(status().isNotFound());

        assertThat(expenseClaimRepository.count()).isEqualTo(claimsBefore);
    }

    @Test
    void signedOutCallerGets401() throws Exception {
        currentEmployee.signOut();

        mockMvc.perform(get("/api/claims/mine"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Authentication required"));
    }

    // ---- Helpers ----

    private Long submitClaim(String json) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated())
                .andReturn();
        String location = result.getResponse().getHeader("Location");
        return Long.valueOf(location.substring(location.lastIndexOf('/') + 1));
    }

    private static String claimJson(String category, String amount, LocalDate expenseDate, String description) {
        String descriptionJson = description == null ? "null" : "\"" + description + "\"";
        return """
                {"category": "%s", "amount": %s, "expenseDate": "%s", "description": %s}
                """.formatted(category, new BigDecimal(amount).toPlainString(), expenseDate, descriptionJson);
    }

    // Error responses must not reveal how the application is built.
    private static void assertNoInternalDetails(String body) {
        assertThat(body).doesNotContainIgnoringCase("exception")
                .doesNotContain("tools.jackson", "com.example", "at org.", "SQL", "stackTrace", "trace");
    }
}
