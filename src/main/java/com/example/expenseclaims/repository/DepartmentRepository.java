package com.example.expenseclaims.repository;

import com.example.expenseclaims.entity.Department;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Database access for departments. JpaRepository already provides save, findById, findAll and delete.
 */
public interface DepartmentRepository extends JpaRepository<Department, Long> {
}
