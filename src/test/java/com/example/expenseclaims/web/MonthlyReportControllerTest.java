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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/reports/monthly-expenses-by-category.
 *
 * "Today" is fixed to 2025-02-15 (UTC) with a test Clock, so the current month is February 2025:
 * from 2025-02-01 (included) to 2025-03-01 (excluded). February also checks a short month's last day.
 *
 *   owner (dept)  category  amount  expense date  status     counted?
 *   Ravi (Sales)  TRAVEL    100.00  2025-02-01    APPROVED   yes (first day)
 *   Ravi (Sales)  TRAVEL     50.25  2025-02-28    APPROVED   yes (last day)
 *   Ravi (Sales)  MEALS      30.00  2025-02-10    APPROVED   yes
 *   Ravi (Sales)  MEALS     999.00  2025-02-11    SUBMITTED  no (not approved)
 *   Ravi (Sales)  MEALS     888.00  2025-02-12    REJECTED   no (not approved)
 *   Ravi (Sales)  TRAVEL    777.00  2025-01-31    APPROVED   no (previous month)
 *   Ravi (Sales)  TRAVEL    666.00  2025-03-01    APPROVED   no (next month)
 *   Ian (IT)      TRAINING  400.00  2025-02-14    APPROVED   yes
 *   Ian (IT)      TRAVEL     10.00  2025-02-20    APPROVED   yes
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MonthlyReportControllerTest {

    @TestConfiguration
    static class FixedClockConfiguration {

        // Replaces the system clock (ClockConfig) for this test class only.
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2025-02-15T12:00:00Z"), ZoneOffset.UTC);
        }
    }

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

    private Employee ravi;
    private Employee ian;
    private Employee salesManager;
    private Employee itManager;
    private Employee finance;
    private Department legal;

    @BeforeEach
    void createPeople() {
        Department sales = departmentRepository.save(new Department("Sales"));
        Department it = departmentRepository.save(new Department("IT"));
        Department accounts = departmentRepository.save(new Department("Accounts"));
        legal = departmentRepository.save(new Department("Legal"));
        ravi = employeeRepository.save(new Employee("Ravi Kumar", "ravi@example.com", "hash", Role.EMPLOYEE, sales));
        ian = employeeRepository.save(new Employee("Ian Lee", "ian@example.com", "hash", Role.EMPLOYEE, it));
        salesManager = employeeRepository.save(new Employee("Karan Shah", "karan@example.com", "hash", Role.MANAGER, sales));
        itManager = employeeRepository.save(new Employee("Irfan Khan", "irfan@example.com", "hash", Role.MANAGER, it));
        finance = employeeRepository.save(new Employee("Fatima Ali", "fatima@example.com", "hash", Role.FINANCE, accounts));
    }

    private void createClaims() {
        claim(ravi, ExpenseCategory.TRAVEL, "100.00", "2025-02-01", ClaimStatus.APPROVED);
        claim(ravi, ExpenseCategory.TRAVEL, "50.25", "2025-02-28", ClaimStatus.APPROVED);
        claim(ravi, ExpenseCategory.MEALS, "30.00", "2025-02-10", ClaimStatus.APPROVED);
        claim(ravi, ExpenseCategory.MEALS, "999.00", "2025-02-11", ClaimStatus.SUBMITTED);
        claim(ravi, ExpenseCategory.MEALS, "888.00", "2025-02-12", ClaimStatus.REJECTED);
        claim(ravi, ExpenseCategory.TRAVEL, "777.00", "2025-01-31", ClaimStatus.APPROVED);
        claim(ravi, ExpenseCategory.TRAVEL, "666.00", "2025-03-01", ClaimStatus.APPROVED);
        claim(ian, ExpenseCategory.TRAINING, "400.00", "2025-02-14", ClaimStatus.APPROVED);
        claim(ian, ExpenseCategory.TRAVEL, "10.00", "2025-02-20", ClaimStatus.APPROVED);
    }

    @Test
    void financeSeesOrganisationWideTotalsPerCategory() throws Exception {
        createClaims();

        report(finance)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2025-02"))
                .andExpect(jsonPath("$.periodStart").value("2025-02-01"))
                .andExpect(jsonPath("$.periodEndExclusive").value("2025-03-01"))
                .andExpect(jsonPath("$.scope").value("ORGANIZATION"))
                .andExpect(jsonPath("$.departmentName").doesNotExist())
                .andExpect(jsonPath("$.totals[*].category", contains("MEALS", "TRAINING", "TRAVEL")))
                .andExpect(jsonPath("$.totals[*].total", contains(30.00, 400.00, 160.25)))
                .andExpect(jsonPath("$.grandTotal").value(590.25));
    }

    @Test
    void managerSeesOnlyTheirDepartmentsTotals() throws Exception {
        createClaims();

        // Sales: TRAVEL 100.00 + 50.25 (first and last day of February), MEALS 30.00.
        report(salesManager)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope").value("DEPARTMENT"))
                .andExpect(jsonPath("$.departmentName").value("Sales"))
                .andExpect(jsonPath("$.totals[*].category", contains("MEALS", "TRAVEL")))
                .andExpect(jsonPath("$.totals[*].total", contains(30.00, 150.25)))
                .andExpect(jsonPath("$.grandTotal").value(180.25));

        report(itManager)
                .andExpect(jsonPath("$.departmentName").value("IT"))
                .andExpect(jsonPath("$.totals[*].category", contains("TRAINING", "TRAVEL")))
                .andExpect(jsonPath("$.totals[*].total", contains(400.00, 10.00)))
                .andExpect(jsonPath("$.grandTotal").value(410.00));
    }

    @Test
    void emptyMonthGivesNoTotalsAndAZeroGrandTotal() throws Exception {
        // No claims at all this month.
        report(finance)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totals", empty()))
                .andExpect(jsonPath("$.grandTotal").value(0.00));
    }

    @Test
    void managerOfADepartmentWithoutApprovedClaimsGetsAnEmptyReport() throws Exception {
        createClaims();
        Employee legalManager = employeeRepository.save(
                new Employee("Lena Ortiz", "lena@example.com", "hash", Role.MANAGER, legal));

        report(legalManager)
                .andExpect(jsonPath("$.departmentName").value("Legal"))
                .andExpect(jsonPath("$.totals", empty()))
                .andExpect(jsonPath("$.grandTotal").value(0.00));
    }

    @Test
    void employeesCannotSeeReports() throws Exception {
        report(ravi).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Not allowed"));
        mockMvc.perform(get("/api/reports/monthly-expenses-by-category")).andExpect(status().isUnauthorized());
    }

    @Test
    void departmentParameterCannotWidenAManagersReport() throws Exception {
        createClaims();

        mockMvc.perform(get("/api/reports/monthly-expenses-by-category")
                        .param("departmentId", String.valueOf(ian.getDepartment().getId()))
                        .param("scope", "ORGANIZATION")
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(salesManager)))
                .andExpect(jsonPath("$.departmentName").value("Sales"))
                .andExpect(jsonPath("$.grandTotal").value(180.25));
    }

    @Test
    void totalsAreComputedInTheDatabaseWithoutLoadingClaims() throws Exception {
        createClaims();
        entityManager.flush();
        entityManager.clear();
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
        try {
            report(finance).andExpect(status().isOk());

            // 1 query for the caller + 1 aggregate (SUM ... GROUP BY) query; no claim entity is loaded.
            assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(2);
            assertThat(statistics.getEntityStatistics(ExpenseClaim.class.getName()).getLoadCount()).isZero();
        } finally {
            statistics.setStatisticsEnabled(false);
        }
    }

    private void claim(Employee owner, ExpenseCategory category, String amount, String date, ClaimStatus status) {
        ExpenseClaim claim = new ExpenseClaim(owner, category, new BigDecimal(amount), LocalDate.parse(date), null);
        claim.setStatus(status);
        expenseClaimRepository.saveAndFlush(claim);
    }

    private ResultActions report(Employee caller) throws Exception {
        return mockMvc.perform(get("/api/reports/monthly-expenses-by-category")
                .header(HttpHeaders.AUTHORIZATION, tokenFor(caller)));
    }

    private String tokenFor(Employee employee) {
        return "Bearer " + jwtTokenService.issueToken(employee).value();
    }
}
