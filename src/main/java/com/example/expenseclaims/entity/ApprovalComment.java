package com.example.expenseclaims.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A comment on an expense claim. A claim can have many comments, written by different people.
 * Comments are never edited, so there is no updatedAt field.
 */
@Entity
@Table(name = "approval_comments")
public class ApprovalComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id", nullable = false)
    private ExpenseClaim claim;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private Employee author;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommentAction action;

    @Column(name = "comment_text", length = 1000)
    private String commentText;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ApprovalComment() {
    }

    public ApprovalComment(ExpenseClaim claim, Employee author, CommentAction action, String commentText) {
        this.claim = claim;
        this.author = author;
        this.action = action;
        this.commentText = commentText;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public ExpenseClaim getClaim() {
        return claim;
    }

    public Employee getAuthor() {
        return author;
    }

    public CommentAction getAction() {
        return action;
    }

    public String getCommentText() {
        return commentText;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
