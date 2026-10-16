package com.example.expenseclaims.repository;

import com.example.expenseclaims.entity.Department;
import com.example.expenseclaims.entity.Employee;
import com.example.expenseclaims.entity.Role;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * @DataJpaTest starts only the database part of the application (JPA, repositories, Flyway).
 * Each test runs in a transaction that is rolled back afterwards, so tests do not affect each other.
 * replace = NONE tells Spring to use our real PostgreSQL test database instead of an in-memory one.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
class EmployeeRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private EmployeeRepository employeeRepository;

    @Test
    void findByEmailReturnsEmployeeWithDepartment() {
        Department engineering = entityManager.persist(new Department("Engineering"));
        entityManager.persist(new Employee("Asha Rao", "asha@example.com", "hash", Role.MANAGER, engineering));
        // Write to the database and empty the session, so the next read really comes from PostgreSQL.
        entityManager.flush();
        entityManager.clear();

        Optional<Employee> found = employeeRepository.findByEmail("asha@example.com");

        assertThat(found).isPresent();
        assertThat(found.get().getFullName()).isEqualTo("Asha Rao");
        assertThat(found.get().getRole()).isEqualTo(Role.MANAGER);
        assertThat(found.get().getDepartment().getName()).isEqualTo("Engineering");
    }

    @Test
    void findByEmailReturnsEmptyForUnknownEmail() {
        assertThat(employeeRepository.findByEmail("nobody@example.com")).isEmpty();
    }

    @Test
    void duplicateEmailIsRejectedByDatabase() {
        Department engineering = entityManager.persist(new Department("Engineering"));
        employeeRepository.saveAndFlush(
                new Employee("First Person", "same@example.com", "hash", Role.EMPLOYEE, engineering));

        Employee duplicate = new Employee("Second Person", "same@example.com", "hash", Role.EMPLOYEE, engineering);

        assertThatThrownBy(() -> employeeRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
