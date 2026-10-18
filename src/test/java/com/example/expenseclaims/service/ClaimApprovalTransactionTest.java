package com.example.expenseclaims.service;

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
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests that need real, committed transactions: rollback when a decision cannot be completed, and
 * decisions racing each other. Unlike most tests, this class is NOT @Transactional, so it creates
 * its own uniquely named rows in expense_claims_test and deletes them again after each test.
 */
@SpringBootTest
@ActiveProfiles("test")
class ClaimApprovalTransactionTest {

    @Autowired
    private ClaimApprovalService claimApprovalService;

    @Autowired
    private DepartmentRepository departmentRepository;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Autowired
    private ExpenseClaimRepository expenseClaimRepository;

    @Autowired
    private ApprovalCommentRepository approvalCommentRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Department department;
    private Employee owner;
    private Employee managerA;
    private Employee managerB;
    private ExpenseClaim claim;

    @BeforeEach
    void createCommittedData() {
        String unique = UUID.randomUUID().toString();
        department = departmentRepository.save(new Department("Concurrency test " + unique));
        owner = employeeRepository.save(new Employee("Owner", "owner-" + unique + "@example.com", "hash", Role.EMPLOYEE, department));
        managerA = employeeRepository.save(new Employee("Manager A", "a-" + unique + "@example.com", "hash", Role.MANAGER, department));
        managerB = employeeRepository.save(new Employee("Manager B", "b-" + unique + "@example.com", "hash", Role.MANAGER, department));
        claim = expenseClaimRepository.save(new ExpenseClaim(owner, ExpenseCategory.TRAVEL,
                new BigDecimal("80.00"), LocalDate.now().minusDays(2), "Train"));
    }

    @AfterEach
    void deleteCommittedData() {
        jdbcTemplate.update("DELETE FROM approval_comments WHERE claim_id IN "
                + "(SELECT id FROM expense_claims WHERE employee_id IN (SELECT id FROM employees WHERE department_id = ?))",
                department.getId());
        jdbcTemplate.update("DELETE FROM expense_claims WHERE employee_id IN (SELECT id FROM employees WHERE department_id = ?)",
                department.getId());
        jdbcTemplate.update("DELETE FROM employees WHERE department_id = ?", department.getId());
        jdbcTemplate.update("DELETE FROM departments WHERE id = ?", department.getId());
    }

    @Test
    void whenTheAuditCommentCannotBeSavedTheStatusChangeIsRolledBack() {
        // 1001 characters do not fit approval_comments.comment_text VARCHAR(1000). The HTTP layer refuses
        // this earlier; calling the service directly lets the database fail AFTER the claim's UPDATE ran.
        String tooLong = "x".repeat(1001);

        assertThatThrownBy(() -> claimApprovalService.approveClaim(managerA.getId(), claim.getId(), tooLong))
                .isInstanceOf(DataAccessException.class);

        // Neither the new status nor the comment was saved.
        ExpenseClaim reloaded = expenseClaimRepository.findById(claim.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ClaimStatus.SUBMITTED);
        assertThat(reloaded.getVersion()).isEqualTo(0L);
        assertThat(approvalCommentRepository.findByClaimIdOrderByCreatedAtAscIdAsc(claim.getId())).isEmpty();

        // And the claim can still be decided normally afterwards.
        claimApprovalService.approveClaim(managerA.getId(), claim.getId(), "Fine");
        assertThat(expenseClaimRepository.findById(claim.getId()).orElseThrow().getStatus()).isEqualTo(ClaimStatus.APPROVED);
    }

    @Test
    void aDecisionBasedOnAStaleReadIsRejectedByTheVersionCheck() {
        // Deterministic version of a race: transaction 1 reads the claim, transaction 2 approves it
        // and commits, then transaction 1 tries to save its own change based on the old version.
        TransactionTemplate outer = new TransactionTemplate(transactionManager);
        TransactionTemplate independent = new TransactionTemplate(transactionManager);
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        assertThatThrownBy(() -> outer.executeWithoutResult(status -> {
            ExpenseClaim staleCopy = entityManager.find(ExpenseClaim.class, claim.getId());
            assertThat(staleCopy.getVersion()).isEqualTo(0L);

            independent.executeWithoutResult(inner ->
                    claimApprovalService.approveClaim(managerB.getId(), claim.getId(), "Approved first"));

            // Saved through the repository, exactly as ClaimApprovalService saves a decision.
            staleCopy.setStatus(ClaimStatus.REJECTED);
            expenseClaimRepository.saveAndFlush(staleCopy);
        })).isInstanceOf(OptimisticLockingFailureException.class);

        ExpenseClaim reloaded = expenseClaimRepository.findById(claim.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        assertThat(reloaded.getVersion()).isEqualTo(1L);
    }

    @Test
    void concurrentDecisionsOnOneClaimCannotBothSucceed() throws Exception {
        int requests = 8;
        CountDownLatch ready = new CountDownLatch(requests);
        CountDownLatch go = new CountDownLatch(1);
        List<Callable<Outcome>> tasks = new ArrayList<>();
        for (int i = 0; i < requests; i++) {
            boolean approve = i % 2 == 0;
            Employee manager = i % 4 < 2 ? managerA : managerB;
            tasks.add(() -> {
                ready.countDown();
                go.await();
                try {
                    if (approve) {
                        claimApprovalService.approveClaim(manager.getId(), claim.getId(), "approve " + manager.getId());
                    } else {
                        claimApprovalService.rejectClaim(manager.getId(), claim.getId(), "reject " + manager.getId());
                    }
                    return Outcome.SUCCEEDED;
                } catch (InvalidClaimStateException | OptimisticLockingFailureException conflict) {
                    return Outcome.CONFLICT;
                }
            });
        }

        ExecutorService pool = Executors.newFixedThreadPool(requests);
        List<Future<Outcome>> futures = new ArrayList<>();
        try {
            for (Callable<Outcome> task : tasks) {
                futures.add(pool.submit(task));
            }
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown(); // release all requests at the same moment
        } finally {
            pool.shutdown();
            assertThat(pool.awaitTermination(60, TimeUnit.SECONDS)).isTrue();
        }

        List<Outcome> outcomes = new ArrayList<>();
        for (Future<Outcome> future : futures) {
            outcomes.add(future.get()); // any unexpected exception fails the test here
        }

        // Exactly one decision won; every other request was told there was a conflict.
        assertThat(outcomes).filteredOn(outcome -> outcome == Outcome.SUCCEEDED).hasSize(1);
        assertThat(outcomes).filteredOn(outcome -> outcome == Outcome.CONFLICT).hasSize(requests - 1);

        // The database agrees: one status change and one audit comment that matches it.
        ExpenseClaim reloaded = expenseClaimRepository.findById(claim.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isIn(ClaimStatus.APPROVED, ClaimStatus.REJECTED);
        assertThat(reloaded.getVersion()).isEqualTo(1L);

        List<ApprovalComment> comments = approvalCommentRepository.findByClaimIdOrderByCreatedAtAscIdAsc(claim.getId());
        assertThat(comments).hasSize(1);
        CommentAction expectedAction = reloaded.getStatus() == ClaimStatus.APPROVED ? CommentAction.APPROVE : CommentAction.REJECT;
        assertThat(comments.getFirst().getAction()).isEqualTo(expectedAction);
    }

    @Test
    void serviceRefusesNonManagersAndRejectionWithoutReason() {
        assertThatThrownBy(() -> claimApprovalService.approveClaim(owner.getId(), claim.getId(), null))
                .isInstanceOf(ClaimDecisionNotAllowedException.class);
        assertThatThrownBy(() -> claimApprovalService.rejectClaim(managerA.getId(), claim.getId(), "   "))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(expenseClaimRepository.findById(claim.getId()).orElseThrow().getStatus()).isEqualTo(ClaimStatus.SUBMITTED);
        assertThat(approvalCommentRepository.findByClaimIdOrderByCreatedAtAscIdAsc(claim.getId())).isEmpty();
    }

    private enum Outcome { SUCCEEDED, CONFLICT }
}
