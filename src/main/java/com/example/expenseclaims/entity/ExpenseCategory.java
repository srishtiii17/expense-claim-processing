package com.example.expenseclaims.entity;

/**
 * Allowed expense categories. This fixed list is an assumption until the mentor confirms it.
 * If a value is added here, a Flyway migration must also update the database CHECK constraint.
 */
public enum ExpenseCategory {
    TRAVEL,
    MEALS,
    ACCOMMODATION,
    OFFICE_SUPPLIES,
    TRAINING,
    OTHER
}
