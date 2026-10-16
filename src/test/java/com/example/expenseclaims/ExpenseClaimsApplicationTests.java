package com.example.expenseclaims;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Starts the whole application against the PostgreSQL test database.
 * If this passes, Flyway ran the migrations and Hibernate confirmed ("validate")
 * that every entity matches its table.
 */
@SpringBootTest
@ActiveProfiles("test")
class ExpenseClaimsApplicationTests {

    @Test
    void contextLoads() {
    }
}
