package com.example.expenseclaims.dto;

import com.example.expenseclaims.entity.Employee;

/**
 * The public view of an employee. It deliberately leaves out passwordHash and email.
 *
 * A Java "record" is a compact class for holding data: Java generates the constructor,
 * the accessor methods (id(), fullName(), ...), equals, hashCode and toString.
 */
public record EmployeeSummaryResponse(
        Long id,
        String fullName,
        String departmentName
) {

    // Must be called while the database session is open, because the department is loaded lazily.
    public static EmployeeSummaryResponse fromEntity(Employee employee) {
        return new EmployeeSummaryResponse(
                employee.getId(),
                employee.getFullName(),
                employee.getDepartment().getName()
        );
    }
}
