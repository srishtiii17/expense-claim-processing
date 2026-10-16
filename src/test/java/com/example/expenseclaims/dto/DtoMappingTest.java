package com.example.expenseclaims.dto;

import com.example.expenseclaims.entity.ApprovalComment;
import com.example.expenseclaims.entity.ClaimStatus;
import com.example.expenseclaims.entity.CommentAction;
import com.example.expenseclaims.entity.Department;
import com.example.expenseclaims.entity.Employee;
import com.example.expenseclaims.entity.ExpenseCategory;
import com.example.expenseclaims.entity.ExpenseClaim;
import com.example.expenseclaims.entity.Role;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain unit tests: no Spring and no database are needed, so these run very fast.
 */
class DtoMappingTest {

    private final Department department = new Department("Engineering");
    private final Employee employee =
            new Employee("Asha Rao", "asha@example.com", "secret-hash", Role.EMPLOYEE, department);

    @Test
    void claimResponseCopiesClaimFieldsAndOwnerSummary() {
        ExpenseClaim claim = new ExpenseClaim(employee, ExpenseCategory.MEALS,
                new BigDecimal("42.00"), LocalDate.of(2026, 10, 3), "Team lunch");

        ExpenseClaimResponse response = ExpenseClaimResponse.fromEntity(claim);

        assertThat(response.category()).isEqualTo(ExpenseCategory.MEALS);
        assertThat(response.amount()).isEqualByComparingTo("42.00");
        assertThat(response.expenseDate()).isEqualTo(LocalDate.of(2026, 10, 3));
        assertThat(response.description()).isEqualTo("Team lunch");
        assertThat(response.status()).isEqualTo(ClaimStatus.SUBMITTED);
        assertThat(response.submittedBy().fullName()).isEqualTo("Asha Rao");
        assertThat(response.submittedBy().departmentName()).isEqualTo("Engineering");
    }

    @Test
    void commentResponseCopiesCommentAndAuthor() {
        ExpenseClaim claim = new ExpenseClaim(employee, ExpenseCategory.OTHER,
                new BigDecimal("5.00"), LocalDate.of(2026, 10, 3), null);
        ApprovalComment comment = new ApprovalComment(claim, employee, CommentAction.COMMENT, "Receipt attached");

        ApprovalCommentResponse response = ApprovalCommentResponse.fromEntity(comment);

        assertThat(response.action()).isEqualTo(CommentAction.COMMENT);
        assertThat(response.commentText()).isEqualTo("Receipt attached");
        assertThat(response.author().fullName()).isEqualTo("Asha Rao");
    }

    @Test
    void employeeSummaryDoesNotExposeSensitiveFields() {
        // Security check: the public employee DTO must never contain the password hash or email.
        List<String> fieldNames = Arrays.stream(EmployeeSummaryResponse.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(fieldNames).containsExactly("id", "fullName", "departmentName");
        assertThat(fieldNames).doesNotContain("passwordHash", "email");
    }

    @Test
    void createRequestDoesNotLetClientChooseOwnerOrStatus() {
        // Security check: the owner comes from the login token, and new claims always start as SUBMITTED.
        List<String> fieldNames = Arrays.stream(CreateExpenseClaimRequest.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();

        assertThat(fieldNames).doesNotContain("employeeId", "status", "id");
    }
}
