package com.example.expenseclaims.repository;

import com.example.expenseclaims.entity.ApprovalComment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ApprovalCommentRepository extends JpaRepository<ApprovalComment, Long> {

    // All comments for one claim, oldest first. Uses the index on approval_comments(claim_id).
    List<ApprovalComment> findByClaimIdOrderByCreatedAtAscIdAsc(Long claimId);
}
