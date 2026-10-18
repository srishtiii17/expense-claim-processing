package com.example.expenseclaims.service;

import com.example.expenseclaims.dto.CreateExpenseClaimRequest;
import com.example.expenseclaims.entity.Department;
import com.example.expenseclaims.entity.Employee;
import com.example.expenseclaims.entity.ExpenseCategory;
import com.example.expenseclaims.entity.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The service enforces its own rules too, so they hold even for a caller that does not go through
 * the HTTP security filter (for example a future scheduled job or another service).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(ExpenseClaimService.class)
class ExpenseClaimServiceTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ExpenseClaimService expenseClaimService;

    private Department sales;

    @BeforeEach
    void createDepartment() {
        sales = entityManager.persist(new Department("Sales"));
    }

    @Test
    void managerAndFinanceCannotSubmitEvenWithoutTheHttpLayer() {
        Employee manager = entityManager.persist(new Employee("Karan Shah", "karan@example.com", "hash", Role.MANAGER, sales));
        Employee finance = entityManager.persist(new Employee("Fatima Ali", "fatima@example.com", "hash", Role.FINANCE, sales));

        assertThatThrownBy(() -> expenseClaimService.submitClaim(manager.getId(), validRequest()))
                .isInstanceOf(ClaimSubmissionNotAllowedException.class);
        assertThatThrownBy(() -> expenseClaimService.submitClaim(finance.getId(), validRequest()))
                .isInstanceOf(ClaimSubmissionNotAllowedException.class);
    }

    @Test
    void unknownEmployeeCannotSubmit() {
        assertThatThrownBy(() -> expenseClaimService.submitClaim(Long.MAX_VALUE, validRequest()))
                .isInstanceOf(EmployeeNotFoundException.class);
    }

    private static CreateExpenseClaimRequest validRequest() {
        return new CreateExpenseClaimRequest(ExpenseCategory.MEALS, new BigDecimal("20.00"),
                LocalDate.now().minusDays(1), "Lunch");
    }
}
