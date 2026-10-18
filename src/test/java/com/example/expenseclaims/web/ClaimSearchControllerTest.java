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
import com.example.expenseclaims.security.JwtTokenService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/claims (dynamic filters + role-based visibility) and GET /api/claims/{id} visibility.
 *
 * Test data (created fresh for every test and rolled back afterwards):
 *
 *   claim  owner (dept)    category         amount   expense date  status
 *   c1     Ravi (Sales)    TRAVEL            125.50  2026-09-05    SUBMITTED
 *   c2     Ravi (Sales)    MEALS              20.00  2026-09-20    APPROVED
 *   c3     Ravi (Sales)    OFFICE_SUPPLIES   600.00  2026-10-02    REJECTED
 *   c4     Priya (Sales)   TRAVEL           5000.00  2026-10-10    SUBMITTED
 *   c5     Ian (IT)        TRAVEL             99.99  2026-10-01    SUBMITTED
 *   c6     Ian (IT)        TRAINING         1500.00  2026-08-31    APPROVED
 *
 * Results are newest first (created last = first), so c6 comes before c5, and so on.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ClaimSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenService jwtTokenService;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private ExpenseClaimRepository expenseClaimRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Department it;
    private Employee ravi;
    private Employee priya;
    private Employee ian;
    private Employee salesManager;
    private Employee itManager;
    private Employee finance;
    private Long c1, c2, c3, c4, c5, c6;

    @BeforeEach
    void createData() {
        Department sales = departmentRepository.save(new Department("Sales"));
        it = departmentRepository.save(new Department("IT"));
        Department accounts = departmentRepository.save(new Department("Accounts"));
        ravi = employeeRepository.save(new Employee("Ravi Kumar", "ravi@example.com", "hash", Role.EMPLOYEE, sales));
        priya = employeeRepository.save(new Employee("Priya Nair", "priya@example.com", "hash", Role.EMPLOYEE, sales));
        ian = employeeRepository.save(new Employee("Ian Lee", "ian@example.com", "hash", Role.EMPLOYEE, it));
        salesManager = employeeRepository.save(new Employee("Karan Shah", "karan@example.com", "hash", Role.MANAGER, sales));
        itManager = employeeRepository.save(new Employee("Irfan Khan", "irfan@example.com", "hash", Role.MANAGER, it));
        finance = employeeRepository.save(new Employee("Fatima Ali", "fatima@example.com", "hash", Role.FINANCE, accounts));

        c1 = claim(ravi, ExpenseCategory.TRAVEL, "125.50", "2026-09-05", ClaimStatus.SUBMITTED);
        c2 = claim(ravi, ExpenseCategory.MEALS, "20.00", "2026-09-20", ClaimStatus.APPROVED);
        c3 = claim(ravi, ExpenseCategory.OFFICE_SUPPLIES, "600.00", "2026-10-02", ClaimStatus.REJECTED);
        c4 = claim(priya, ExpenseCategory.TRAVEL, "5000.00", "2026-10-10", ClaimStatus.SUBMITTED);
        c5 = claim(ian, ExpenseCategory.TRAVEL, "99.99", "2026-10-01", ClaimStatus.SUBMITTED);
        c6 = claim(ian, ExpenseCategory.TRAINING, "1500.00", "2026-08-31", ClaimStatus.APPROVED);
    }

    // ---- Visibility with no filters ----

    @Test
    void employeeSeesOnlyTheirOwnClaims() throws Exception {
        search(ravi, "").andExpect(ids(c3, c2, c1))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void managerSeesOnlyTheirDepartmentsClaims() throws Exception {
        search(salesManager, "").andExpect(ids(c4, c3, c2, c1));
        search(itManager, "").andExpect(ids(c6, c5));
    }

    @Test
    void financeSeesClaimsFromAllDepartments() throws Exception {
        search(finance, "").andExpect(ids(c6, c5, c4, c3, c2, c1))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void responsesAreDtosWithoutInternalFields() throws Exception {
        search(finance, "?category=TRAINING")
                .andExpect(ids(c6))
                .andExpect(jsonPath("$.content[0].submittedBy.fullName").value("Ian Lee"))
                .andExpect(jsonPath("$.content[0].submittedBy.departmentName").value("IT"))
                .andExpect(jsonPath("$.content[0].version").doesNotExist())
                .andExpect(jsonPath("$.content[0].submittedBy.email").doesNotExist())
                .andExpect(jsonPath("$.content[0].submittedBy.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.content[0].employee").doesNotExist());
    }

    // ---- Each filter on its own (as finance, who sees everything) ----

    @Test
    void filterByStatus() throws Exception {
        search(finance, "?status=SUBMITTED").andExpect(ids(c5, c4, c1));
        search(finance, "?status=APPROVED").andExpect(ids(c6, c2));
        search(finance, "?status=REJECTED").andExpect(ids(c3));
    }

    @Test
    void filterByCategory() throws Exception {
        search(finance, "?category=TRAVEL").andExpect(ids(c5, c4, c1));
        search(finance, "?category=ACCOMMODATION").andExpect(jsonPath("$.content", empty()));
    }

    @Test
    void filterByMinimumAmountIsInclusive() throws Exception {
        search(finance, "?minAmount=600").andExpect(ids(c6, c4, c3));
    }

    @Test
    void filterByMaximumAmountIsInclusive() throws Exception {
        search(finance, "?maxAmount=99.99").andExpect(ids(c5, c2));
    }

    @Test
    void filterByStartDateIsInclusive() throws Exception {
        search(finance, "?startDate=2026-10-01").andExpect(ids(c5, c4, c3));
    }

    @Test
    void filterByEndDateIsInclusive() throws Exception {
        search(finance, "?endDate=2026-09-05").andExpect(ids(c6, c1));
    }

    // ---- Combinations ----

    @Test
    void allFiltersCombine() throws Exception {
        String all = "?status=SUBMITTED&category=TRAVEL&minAmount=100&maxAmount=5000&startDate=2026-10-01&endDate=2026-10-31";
        search(finance, all).andExpect(ids(c4));
        search(itManager, all).andExpect(jsonPath("$.content", empty()));
    }

    @Test
    void amountAndDateRangesCombine() throws Exception {
        search(finance, "?minAmount=50&maxAmount=1000&startDate=2026-09-01&endDate=2026-10-05")
                .andExpect(ids(c5, c3, c1));
    }

    @Test
    void equalBoundsAreAllowed() throws Exception {
        search(finance, "?minAmount=125.50&maxAmount=125.50&startDate=2026-09-05&endDate=2026-09-05")
                .andExpect(ids(c1));
    }

    @Test
    void filtersNeverWidenAnEmployeesView() throws Exception {
        // Ravi asks for all TRAVEL claims: only his own c1, not Priya's c4 or Ian's c5.
        search(ravi, "?category=TRAVEL").andExpect(ids(c1));
    }

    @Test
    void filtersNeverWidenAManagersView() throws Exception {
        // The Sales manager asks for every SUBMITTED claim: Ian's c5 (IT) stays hidden.
        search(salesManager, "?status=SUBMITTED").andExpect(ids(c4, c1));
    }

    @Test
    void employeeOrDepartmentIdsInTheRequestAreIgnored() throws Exception {
        search(ravi, "?employeeId=" + ian.getId() + "&departmentId=" + it.getId())
                .andExpect(ids(c3, c2, c1));
        search(salesManager, "?departmentId=" + it.getId())
                .andExpect(ids(c4, c3, c2, c1));
    }

    // ---- Invalid input ----

    @Test
    void minAmountGreaterThanMaxAmountIs400() throws Exception {
        search(finance, "?minAmount=500&maxAmount=100")
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Invalid request"))
                .andExpect(jsonPath("$.detail").value("minAmount must not be greater than maxAmount"));
    }

    @Test
    void startDateAfterEndDateIs400() throws Exception {
        search(finance, "?startDate=2026-10-31&endDate=2026-10-01")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("startDate must not be after endDate"));
    }

    @Test
    void invalidEnumValuesAre400WithoutInternalDetails() throws Exception {
        for (String query : new String[]{"?status=PENDING", "?status=submitted", "?category=PIZZA", "?category=travel"}) {
            assertCleanBadRequest(search(finance, query));
        }
    }

    @Test
    void malformedDatesAndAmountsAre400WithoutInternalDetails() throws Exception {
        for (String query : new String[]{"?startDate=2026-13-01", "?endDate=01/10/2026", "?startDate=yesterday",
                "?minAmount=abc", "?maxAmount=1,000"}) {
            assertCleanBadRequest(search(finance, query));
        }
    }

    // ---- Paging ----

    @Test
    void resultsArePaged() throws Exception {
        search(finance, "?size=2").andExpect(ids(c6, c5))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(3));
        search(finance, "?size=2&page=2").andExpect(ids(c2, c1));
        search(finance, "?size=2&page=5").andExpect(jsonPath("$.content", empty()));
    }

    @Test
    void invalidPagingParametersAre400() throws Exception {
        for (String query : new String[]{"?size=0", "?size=101", "?page=-1", "?size=ten"}) {
            assertCleanBadRequest(search(finance, query));
        }
    }

    @Test
    void searchingRequiresAToken() throws Exception {
        mockMvc.perform(get("/api/claims")).andExpect(status().isUnauthorized());
    }

    // ---- GET /api/claims/{id} uses the same visibility ----

    @Test
    void managerCanReadAClaimFromTheirDepartmentButNotFromAnother() throws Exception {
        read(salesManager, c4).andExpect(status().isOk())
                .andExpect(jsonPath("$.submittedBy.fullName").value("Priya Nair"));
        read(salesManager, c5).andExpect(status().isNotFound());
    }

    @Test
    void financeCanReadAnyClaim() throws Exception {
        read(finance, c1).andExpect(status().isOk());
        read(finance, c5).andExpect(status().isOk());
    }

    @Test
    void employeeStillCannotReadAColleaguesClaim() throws Exception {
        read(ravi, c4).andExpect(status().isNotFound());
        read(ravi, c1).andExpect(status().isOk());
    }

    // ---- Efficiency ----

    @Test
    void listingClaimsDoesNotRunOneQueryPerClaim() throws Exception {
        // Empty the session first, so nothing is served from memory and every load is a real SQL query.
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            search(finance, "").andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(6));

            // 1 query to load the caller + 1 query for the claims with their employees and departments
            // joined in. Without the entity graph this would be about 7 (one per employee and department).
            assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(2);
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    // ---- Helpers ----

    private Long claim(Employee owner, ExpenseCategory category, String amount, String date, ClaimStatus status) {
        ExpenseClaim claim = new ExpenseClaim(owner, category, new BigDecimal(amount), LocalDate.parse(date), null);
        claim.setStatus(status);
        return expenseClaimRepository.saveAndFlush(claim).getId();
    }

    private ResultActions search(Employee caller, String query) throws Exception {
        return mockMvc.perform(withToken(get("/api/claims" + query), caller));
    }

    private ResultActions read(Employee caller, Long claimId) throws Exception {
        return mockMvc.perform(withToken(get("/api/claims/{id}", claimId), caller));
    }

    private MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder request, Employee caller) {
        return request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtTokenService.issueToken(caller).value());
    }

    // Exactly these claim ids, in this order.
    private static org.springframework.test.web.servlet.ResultMatcher ids(Long... expected) {
        return result -> {
            status().isOk().match(result);
            Integer[] asInts = new Integer[expected.length];
            for (int i = 0; i < expected.length; i++) {
                asInts[i] = expected[i].intValue();
            }
            jsonPath("$.content[*].id", contains(asInts)).match(result);
        };
    }

    private static void assertCleanBadRequest(ResultActions result) throws Exception {
        String body = result
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContainIgnoringCase("exception")
                .doesNotContain("java.", "com.example", "trace", "SQL");
    }
}
