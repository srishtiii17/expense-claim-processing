package com.example.expenseclaims.service;

/**
 * Thrown when the employee a request acts for does not exist (for example, a deleted account).
 */
public class EmployeeNotFoundException extends RuntimeException {

    public EmployeeNotFoundException(Long employeeId) {
        super("Employee " + employeeId + " was not found");
    }
}
