package com.example.expenseclaims.security;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Import this into a test with @Import(TestAuthenticationConfiguration.class) to replace the
 * production CurrentEmployeeProvider (which always answers 401) with TestCurrentEmployeeProvider.
 *
 * @Primary makes Spring choose this bean when both providers exist. @TestConfiguration classes
 * are skipped by component scanning, so this only applies to tests that import it.
 */
@TestConfiguration
public class TestAuthenticationConfiguration {

    @Bean
    @Primary
    public TestCurrentEmployeeProvider testCurrentEmployeeProvider() {
        return new TestCurrentEmployeeProvider();
    }
}
