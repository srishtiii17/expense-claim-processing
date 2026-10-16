package com.example.expenseclaims.repository;

import com.example.expenseclaims.entity.ApprovalComment;
import com.example.expenseclaims.entity.CommentAction;
import com.example.expenseclaims.entity.Department;
import com.example.expenseclaims.entity.Employee;
import com.example.expenseclaims.entity.ExpenseCategory;
import com.example.expenseclaims.entity.ExpenseClaim;
import com.example.expenseclaims.entity.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class ApprovalCommentRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ApprovalCommentRepository approvalCommentRepository;

    @Test
    void claimCanHaveMultipleCommentsFromDifferentAuthors() {
        Department marketing = entityManager.persist(new Department("Marketing"));
        Employee owner = entityManager.persist(
                new Employee("Meera Iyer", "meera@example.com", "hash", Role.EMPLOYEE, marketing));
        Employee manager = entityManager.persist(
                new Employee("Karan Shah", "karan@example.com", "hash", Role.MANAGER, marketing));
        ExpenseClaim claim = entityManager.persist(new ExpenseClaim(owner, ExpenseCategory.TRAINING,
                new BigDecimal("450.00"), LocalDate.of(2026, 10, 5), "Online course"));

        approvalCommentRepository.save(new ApprovalComment(claim, manager, CommentAction.COMMENT, "Please attach receipt"));
        approvalCommentRepository.save(new ApprovalComment(claim, owner, CommentAction.COMMENT, "Receipt attached"));
        approvalCommentRepository.save(new ApprovalComment(claim, manager, CommentAction.APPROVE, "Looks good"));
        entityManager.flush();
        entityManager.clear();

        List<ApprovalComment> comments = approvalCommentRepository.findByClaimIdOrderByCreatedAtAscIdAsc(claim.getId());

        assertThat(comments).hasSize(3);
        assertThat(comments).extracting(ApprovalComment::getCommentText)
                .containsExactly("Please attach receipt", "Receipt attached", "Looks good");
        assertThat(comments.get(0).getAuthor().getFullName()).isEqualTo("Karan Shah");
        assertThat(comments.get(1).getAuthor().getFullName()).isEqualTo("Meera Iyer");
        assertThat(comments.get(2).getAction()).isEqualTo(CommentAction.APPROVE);
        assertThat(comments.get(0).getCreatedAt()).isNotNull();
    }
}
