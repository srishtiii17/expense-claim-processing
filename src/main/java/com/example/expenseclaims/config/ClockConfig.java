package com.example.expenseclaims.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * The application's notion of "now". Production uses the system clock in the server's default time
 * zone (assumption A4/A6); tests can replace it with a fixed clock to check month boundaries.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
