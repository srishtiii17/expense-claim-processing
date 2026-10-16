package com.example.expenseclaims.dto;

import com.example.expenseclaims.entity.ApprovalComment;
import com.example.expenseclaims.entity.CommentAction;

import java.time.Instant;

/**
 * What the API returns for one comment on a claim.
 */
public record ApprovalCommentResponse(
        Long id,
        CommentAction action,
        String commentText,
        EmployeeSummaryResponse author,
        Instant createdAt
) {

    public static ApprovalCommentResponse fromEntity(ApprovalComment comment) {
        return new ApprovalCommentResponse(
                comment.getId(),
                comment.getAction(),
                comment.getCommentText(),
                EmployeeSummaryResponse.fromEntity(comment.getAuthor()),
                comment.getCreatedAt()
        );
    }
}
