package com.example.expenseclaims.web;

import com.example.expenseclaims.entity.ApprovalComment;
import com.example.expenseclaims.entity.ClaimStatus;
import com.example.expenseclaims.entity.CommentAction;
import com.example.expenseclaims.entity.Department;
import com.example.expenseclaims.entity.Employee;
import com.example.expenseclaims.entity.ExpenseCategory;
import com.example.expenseclaims.entity.ExpenseClaim;
import com.example.expenseclaims.entity.Role;
import com.example.expenseclaims.repository.ApprovalCommentRepository;
import com.example.expenseclaims.repository.DepartmentRepository;
import com.example.expenseclaims.repository.EmployeeRepository;
import com.example.expenseclaims.repository.ExpenseClaimRepository;
import com.example.expenseclaims.security.JwtTokenService;
import jakarta.persistence.EntityManager;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The manager approval workflow through HTTP, with real JWTs and the PostgreSQL test database.
 * Each test's data is rolled back afterwards.
 *
 * Concurrency and rollback after a failed write need committed transactions, so they are tested
 * separately in ClaimApprovalTransactionTest.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ClaimApprovalControllerTest {

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
    private ApprovalCommentRepository approvalCommentRepository;

    @Autowired
    private EntityManager entityManager;

    private Employee ravi;          // EMPLOYEE in Sales
    private Employee salesManager;  // MANAGER in Sales
    private Employee finance;       // FINANCE in Sales
    private Employee itManager;     // MANAGER in IT (another department)
    private ExpenseClaim claim;     // Ravi's SUBMITTED claim

    @BeforeEach
    void createData() {
        Department sales = departmentRepository.save(new Department("Sales"));
        Department it = departmentRepository.save(new Department("IT"));
        ravi = employeeRepository.save(new Employee("Ravi Kumar", "ravi@example.com", "hash", Role.EMPLOYEE, sales));
        salesManager = employeeRepository.save(new Employee("Karan Shah", "karan@example.com", "hash", Role.MANAGER, sales));
        finance = employeeRepository.save(new Employee("Fatima Ali", "fatima@example.com", "hash", Role.FINANCE, sales));
        itManager = employeeRepository.save(new Employee("Irfan Khan", "irfan@example.com", "hash", Role.MANAGER, it));
        claim = expenseClaimRepository.saveAndFlush(new ExpenseClaim(ravi, ExpenseCategory.TRAVEL,
                new BigDecimal("125.50"), LocalDate.now().minusDays(3), "Taxi to client office"));
    }

    // ---- Successful decisions ----

    @Test
    void managerApprovesASubmittedClaim() throws Exception {
        approve(salesManager, "{\"comment\": \"  Looks good  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claim.id").value(claim.getId()))
                .andExpect(jsonPath("$.claim.status").value("APPROVED"))
                .andExpect(jsonPath("$.claim.submittedBy.id").value(ravi.getId()))
                .andExpect(jsonPath("$.decision.action").value("APPROVE"))
                .andExpect(jsonPath("$.decision.commentText").value("Looks good"))
                .andExpect(jsonPath("$.decision.author.id").value(salesManager.getId()))
                .andExpect(jsonPath("$.decision.author.fullName").value("Karan Shah"))
                .andExpect(jsonPath("$.decision.createdAt").exists())
                .andExpect(jsonPath("$.claim.version").doesNotExist())
                .andExpect(jsonPath("$.decision.author.email").doesNotExist());

        ExpenseClaim saved = reloadClaim();
        assertThat(saved.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(saved.getVersion()).isEqualTo(1L);
        assertOnlyComment(CommentAction.APPROVE, salesManager, "Looks good");
    }

    @Test
    void managerCanApproveWithoutABodyOrComment() throws Exception {
        mockMvc.perform(post("/api/claims/{id}/approve", claim.getId()).header(HttpHeaders.AUTHORIZATION, tokenFor(salesManager)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claim.status").value("APPROVED"))
                .andExpect(jsonPath("$.decision.commentText").doesNotExist());

        assertOnlyComment(CommentAction.APPROVE, salesManager, null);
    }

    @Test
    void managerRejectsASubmittedClaimWithAReason() throws Exception {
        reject(salesManager, "{\"comment\": \"Receipt missing\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claim.status").value("REJECTED"))
                .andExpect(jsonPath("$.decision.action").value("REJECT"))
                .andExpect(jsonPath("$.decision.commentText").value("Receipt missing"))
                .andExpect(jsonPath("$.decision.author.id").value(salesManager.getId()));

        assertThat(reloadClaim().getStatus()).isEqualTo(ClaimStatus.REJECTED);
        assertOnlyComment(CommentAction.REJECT, salesManager, "Receipt missing");
    }

    // ---- Rejection needs a reason ----

    @Test
    void rejectionWithoutAReasonIsRefusedAndChangesNothing() throws Exception {
        for (String body : new String[]{"{}", "{\"comment\": null}", "{\"comment\": \"\"}", "{\"comment\": \"   \"}"}) {
            reject(salesManager, body)
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.errors.comment").value("comment is required when rejecting a claim"));
        }
        mockMvc.perform(post("/api/claims/{id}/reject", claim.getId()).header(HttpHeaders.AUTHORIZATION, tokenFor(salesManager)))
                .andExpect(status().isBadRequest());

        assertUnchanged();
    }

    @Test
    void commentLongerThan1000CharactersIsRefused() throws Exception {
        String longComment = "a".repeat(1001);
        approve(salesManager, "{\"comment\": \"" + longComment + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.comment").value("comment must be at most 1000 characters"));
        reject(salesManager, "{\"comment\": \"" + longComment + "\"}")
                .andExpect(status().isBadRequest());

        assertUnchanged();
    }

    // ---- Who may decide ----

    @Test
    void employeeAndFinanceCannotApproveOrReject() throws Exception {
        for (Employee notAManager : new Employee[]{ravi, finance}) {
            approve(notAManager, "{}")
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.title").value("Not allowed"));
            reject(notAManager, "{\"comment\": \"No\"}")
                    .andExpect(status().isForbidden());
        }

        assertUnchanged();
    }

    @Test
    void nonManagerWithAnInvalidBodyStillGets403() throws Exception {
        // The role check runs before the body is validated.
        reject(ravi, "{}").andExpect(status().isForbidden());
    }

    @Test
    void decisionsRequireAToken() throws Exception {
        mockMvc.perform(post("/api/claims/{id}/approve", claim.getId()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/claims/{id}/reject", claim.getId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"comment\": \"No\"}"))
                .andExpect(status().isUnauthorized());

        assertUnchanged();
    }

    @Test
    void managerFromAnotherDepartmentCannotDecide() throws Exception {
        approve(itManager, "{}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Expense claim " + claim.getId() + " was not found"));
        reject(itManager, "{\"comment\": \"Not my team\"}")
                .andExpect(status().isNotFound());

        assertUnchanged();
    }

    @Test
    void managerCannotDecideTheirOwnClaim() throws Exception {
        // Not possible through the API (managers cannot submit), but a claim can predate a promotion.
        ExpenseClaim managersOwnClaim = expenseClaimRepository.saveAndFlush(new ExpenseClaim(salesManager,
                ExpenseCategory.MEALS, new BigDecimal("40.00"), LocalDate.now().minusDays(1), "Dinner"));

        mockMvc.perform(post("/api/claims/{id}/approve", managersOwnClaim.getId())
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(salesManager)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("You cannot approve or reject your own expense claim"));

        entityManager.flush();
        entityManager.clear();
        assertThat(expenseClaimRepository.findById(managersOwnClaim.getId()).orElseThrow().getStatus())
                .isEqualTo(ClaimStatus.SUBMITTED);
    }

    @Test
    void nonexistentClaimReturns404() throws Exception {
        mockMvc.perform(post("/api/claims/{id}/approve", Long.MAX_VALUE)
                        .header(HttpHeaders.AUTHORIZATION, tokenFor(salesManager)))
                .andExpect(status().isNotFound());
    }

    @Test
    void approverAndStatusCannotBeChosenByTheClient() throws Exception {
        String body = """
                {"comment": "OK", "authorId": %d, "managerId": %d, "employeeId": %d, "status": "REJECTED", "action": "REJECT"}
                """.formatted(itManager.getId(), itManager.getId(), ravi.getId());

        approve(salesManager, body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claim.status").value("APPROVED"))
                .andExpect(jsonPath("$.decision.action").value("APPROVE"))
                .andExpect(jsonPath("$.decision.author.id").value(salesManager.getId()));

        assertOnlyComment(CommentAction.APPROVE, salesManager, "OK");
    }

    // ---- Final states ----

    @Test
    void secondApprovalIsAConflictAndChangesNothing() throws Exception {
        approve(salesManager, "{}").andExpect(status().isOk());

        approve(salesManager, "{}")
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("Conflict"))
                .andExpect(jsonPath("$.detail").value("Expense claim " + claim.getId()
                        + " is already APPROVED; only SUBMITTED claims can be approved or rejected"));

        ExpenseClaim saved = reloadClaim();
        assertThat(saved.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(saved.getVersion()).isEqualTo(1L);
        assertOnlyComment(CommentAction.APPROVE, salesManager, null);
    }

    @Test
    void approvalAfterRejectionIsAConflict() throws Exception {
        reject(salesManager, "{\"comment\": \"Duplicate claim\"}").andExpect(status().isOk());

        approve(salesManager, "{}").andExpect(status().isConflict());

        assertThat(reloadClaim().getStatus()).isEqualTo(ClaimStatus.REJECTED);
        assertOnlyComment(CommentAction.REJECT, salesManager, "Duplicate claim");
    }

    @Test
    void rejectionAfterApprovalIsAConflict() throws Exception {
        approve(salesManager, "{}").andExpect(status().isOk());

        reject(salesManager, "{\"comment\": \"Changed my mind\"}").andExpect(status().isConflict());

        assertThat(reloadClaim().getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertOnlyComment(CommentAction.APPROVE, salesManager, null);
    }

    // ---- Helpers ----

    private ResultActions approve(Employee caller, String json) throws Exception {
        return mockMvc.perform(post("/api/claims/{id}/approve", claim.getId())
                .header(HttpHeaders.AUTHORIZATION, tokenFor(caller))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions reject(Employee caller, String json) throws Exception {
        return mockMvc.perform(post("/api/claims/{id}/reject", claim.getId())
                .header(HttpHeaders.AUTHORIZATION, tokenFor(caller))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String tokenFor(Employee employee) {
        return "Bearer " + jwtTokenService.issueToken(employee).value();
    }

    // Write pending changes and empty the session, so reads really come from PostgreSQL.
    private ExpenseClaim reloadClaim() {
        entityManager.flush();
        entityManager.clear();
        return expenseClaimRepository.findById(claim.getId()).orElseThrow();
    }

    private void assertUnchanged() {
        ExpenseClaim saved = reloadClaim();
        assertThat(saved.getStatus()).isEqualTo(ClaimStatus.SUBMITTED);
        assertThat(saved.getVersion()).isEqualTo(0L);
        assertThat(approvalCommentRepository.findByClaimIdOrderByCreatedAtAscIdAsc(claim.getId())).isEmpty();
    }

    private void assertOnlyComment(CommentAction action, Employee author, String text) {
        entityManager.flush();
        entityManager.clear();
        List<ApprovalComment> comments = approvalCommentRepository.findByClaimIdOrderByCreatedAtAscIdAsc(claim.getId());
        assertThat(comments).hasSize(1);
        assertThat(comments.getFirst().getAction()).isEqualTo(action);
        assertThat(comments.getFirst().getAuthor().getId()).isEqualTo(author.getId());
        assertThat(comments.getFirst().getCommentText()).isEqualTo(text);
        assertThat(comments.getFirst().getCreatedAt()).isNotNull();
    }
}
