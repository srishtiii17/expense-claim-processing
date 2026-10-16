package com.example.expenseclaims.repository;

import com.example.expenseclaims.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    // Spring Data builds the query from the method name: SELECT ... WHERE email = ?
    // Will be used by login in Checkpoint 3.
    Optional<Employee> findByEmail(String email);
}
