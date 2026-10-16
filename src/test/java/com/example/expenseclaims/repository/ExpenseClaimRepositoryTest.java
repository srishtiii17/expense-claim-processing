package com.example.expenseclaims.repository;

import com.example.expenseclaims.entity.ClaimStatus;
import com.example.expenseclaims.entity.Department;
import com.example.expenseclaims.entity.Employee;
import com.example.expenseclaims.entity.ExpenseCategory;
import com.example.expenseclaims.entity.ExpenseClaim;
import com.example.expenseclaims.entity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ExpenseClaimRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ExpenseClaimRepository expenseClaimRepository;

    private Employee employee;

    @BeforeEach
    void createEmployee() {
        Department sales = entityManager.persist(new Department("Sales"));
        employee = entityManager.persist(
                new Employee("Ravi Kumar", "ravi@example.com", "hash", Role.EMPLOYEE, sales));
    }

    @Test
    void savedClaimIsLoadedWithOwnerAndDefaultValues() {
        ExpenseClaim claim = new ExpenseClaim(employee, ExpenseCategory.TRAVEL,
                new BigDecimal("125.50"), LocalDate.of(2026, 10, 1), "Taxi to client office");
        Long claimId = expenseClaimRepository.save(claim).getId();
        entityManager.flush();
        entityManager.clear();

        ExpenseClaim loaded = expenseClaimRepository.findById(claimId).orElseThrow();

        assertThat(loaded.getCategory()).isEqualTo(ExpenseCategory.TRAVEL);
        assertThat(loaded.getAmount()).isEqualByComparingTo("125.50");
        assertThat(loaded.getExpenseDate()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(loaded.getStatus()).isEqualTo(ClaimStatus.SUBMITTED);
        assertThat(loaded.getVersion()).isEqualTo(0L);
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
        assertThat(loaded.getEmployee().getFullName()).isEqualTo("Ravi Kumar");
        assertThat(loaded.getEmployee().getDepartment().getName()).isEqualTo("Sales");
    }

    @Test
    void employeeCanHaveMultipleClaims() {
        expenseClaimRepository.save(new ExpenseClaim(employee, ExpenseCategory.MEALS,
                new BigDecimal("20.00"), LocalDate.of(2026, 9, 10), "Lunch"));
        expenseClaimRepository.save(new ExpenseClaim(employee, ExpenseCategory.ACCOMMODATION,
                new BigDecimal("300.00"), LocalDate.of(2026, 9, 11), "Hotel"));
        entityManager.flush();
        entityManager.clear();

        // A plain JPQL query is used here because "find claims by employee" is built in Checkpoint 2.
        List<ExpenseClaim> claims = entityManager.getEntityManager()
                .createQuery("SELECT c FROM ExpenseClaim c WHERE c.employee.id = :employeeId", ExpenseClaim.class)
                .setParameter("employeeId", employee.getId())
                .getResultList();

        assertThat(claims).hasSize(2);
    }

    @Test
    void versionIncreasesWhenClaimIsUpdated() {
        ExpenseClaim claim = expenseClaimRepository.saveAndFlush(new ExpenseClaim(employee, ExpenseCategory.OTHER,
                new BigDecimal("10.00"), LocalDate.of(2026, 10, 2), null));
        assertThat(claim.getVersion()).isEqualTo(0L);

        claim.setStatus(ClaimStatus.APPROVED);
        ExpenseClaim updated = expenseClaimRepository.saveAndFlush(claim);

        assertThat(updated.getVersion()).isEqualTo(1L);
    }

    @Test
    void databaseRejectsZeroAmount() {
        // The API will validate this first (Checkpoint 2); this proves the database CHECK is a safety net.
        ExpenseClaim invalid = new ExpenseClaim(employee, ExpenseCategory.MEALS,
                BigDecimal.ZERO, LocalDate.of(2026, 10, 1), "Invalid amount");

        assertThatThrownBy(() -> expenseClaimRepository.saveAndFlush(invalid))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
