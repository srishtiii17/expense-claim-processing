package com.example.expenseclaims;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point of the application. @SpringBootApplication turns on auto-configuration
 * and scans this package (and sub-packages) for Spring components and entities.
 */
@SpringBootApplication
public class ExpenseClaimsApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExpenseClaimsApplication.class, args);
    }
}
