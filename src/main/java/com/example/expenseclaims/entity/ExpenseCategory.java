package com.example.expenseclaims.entity;

/**
 * Allowed expense categories. This fixed list is an assumption (A7) until the requirements confirm it.
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
