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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /api/claims/pending-approvals: a manager's queue of SUBMITTED claims from their own department,
 * oldest first.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class PendingApprovalsControllerTest {

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

    private Department it;
    private Employee ravi;
    private Employee salesManager;
    private Employee itManager;
    private Employee finance;
    private Long salesSubmittedOld;
    private Long salesSubmittedNew;
    private Long itSubmitted;

    @BeforeEach
    void createData() {
        Department sales = departmentRepository.save(new Department("Sales"));
        it = departmentRepository.save(new Department("IT"));
        ravi = employeeRepository.save(new Employee("Ravi Kumar", "ravi@example.com", "hash", Role.EMPLOYEE, sales));
        Employee priya = employeeRepository.save(new Employee("Priya Nair", "priya@example.com", "hash", Role.EMPLOYEE, sales));
        Employee ian = employeeRepository.save(new Employee("Ian Lee", "ian@example.com", "hash", Role.EMPLOYEE, it));
        salesManager = employeeRepository.save(new Employee("Karan Shah", "karan@example.com", "hash", Role.MANAGER, sales));
        itManager = employeeRepository.save(new Employee("Irfan Khan", "irfan@example.com", "hash", Role.MANAGER, it));
        finance = employeeRepository.save(new Employee("Fatima Ali", "fatima@example.com", "hash", Role.FINANCE, sales));

        salesSubmittedOld = claim(ravi, ClaimStatus.SUBMITTED);
        claim(ravi, ClaimStatus.APPROVED);
        claim(priya, ClaimStatus.REJECTED);
        salesSubmittedNew = claim(priya, ClaimStatus.SUBMITTED);
        itSubmitted = claim(ian, ClaimStatus.SUBMITTED);
        // A SUBMITTED claim of the Sales manager (from before a promotion): they may not decide it.
        claim(salesManager, ClaimStatus.SUBMITTED);
    }

    @Test
    void managerSeesOnlySubmittedClaimsOfTheirOwnDepartmentOldestFirst() throws Exception {
        pending(salesManager, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id", contains(salesSubmittedOld.intValue(), salesSubmittedNew.intValue())))
                .andExpect(jsonPath("$.content[*].status", contains("SUBMITTED", "SUBMITTED")))
                .andExpect(jsonPath("$.content[0].submittedBy.fullName").value("Ravi Kumar"))
                .andExpect(jsonPath("$.content[0].submittedBy.departmentName").value("Sales"))
                .andExpect(jsonPath("$.totalElements").value(2));

        pending(itManager, "")
                .andExpect(jsonPath("$.content[*].id", contains(itSubmitted.intValue())));
    }

    @Test
    void emptyQueueIsAnEmptyPage() throws Exception {
        Department empty = departmentRepository.save(new Department("Legal"));
        Employee legalManager = employeeRepository.save(
                new Employee("Lena Ortiz", "lena@example.com", "hash", Role.MANAGER, empty));

        pending(legalManager, "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", empty()))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.totalPages").value(0));
    }

    @Test
    void decidedClaimsLeaveTheQueue() throws Exception {
        mockMvc.perform(post("/api/claims/{id}/approve", salesSubmittedOld)
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(salesManager)))
                .andExpect(status().isOk());

        pending(salesManager, "")
                .andExpect(jsonPath("$.content[*].id", contains(salesSubmittedNew.intValue())));
    }

    @Test
    void queueIsPaged() throws Exception {
        pending(salesManager, "?size=1")
                .andExpect(jsonPath("$.content[*].id", contains(salesSubmittedOld.intValue())))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
        pending(salesManager, "?size=1&page=1")
                .andExpect(jsonPath("$.content[*].id", contains(salesSubmittedNew.intValue())));
        pending(salesManager, "?size=500").andExpect(status().isBadRequest());
    }

    @Test
    void departmentIdInTheRequestIsIgnored() throws Exception {
        pending(salesManager, "?departmentId=" + it.getId())
                .andExpect(jsonPath("$.content[*].id", contains(salesSubmittedOld.intValue(), salesSubmittedNew.intValue())));
    }

    @Test
    void onlyManagersHaveAQueue() throws Exception {
        pending(ravi, "").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Not allowed"));
        pending(finance, "").andExpect(status().isForbidden());
        mockMvc.perform(get("/api/claims/pending-approvals")).andExpect(status().isUnauthorized());
    }

    private Long claim(Employee owner, ClaimStatus status) {
        ExpenseClaim claim = new ExpenseClaim(owner, ExpenseCategory.TRAVEL, new BigDecimal("50.00"),
                LocalDate.of(2026, 10, 1), null);
        claim.setStatus(status);
        return expenseClaimRepository.saveAndFlush(claim).getId();
    }

    private ResultActions pending(Employee caller, String query) throws Exception {
        return mockMvc.perform(get("/api/claims/pending-approvals" + query).header(HttpHeaders.AUTHORIZATION, tokenFor(caller)));
    }

    private String tokenFor(Employee employee) {
        return "Bearer " + jwtTokenService.issueToken(employee).value();
    }
}
