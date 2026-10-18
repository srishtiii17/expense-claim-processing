package com.example.expenseclaims.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The application must refuse to start without a usable JWT secret, and must never print it.
 * No database needed: ApplicationContextRunner starts a tiny Spring context with only JwtProperties.
 */
class JwtPropertiesTest {

    private static final String VALID_SECRET = "0123456789abcdef0123456789abcdef"; // 32 bytes, test-only

    @Configuration
    @EnableConfigurationProperties(JwtProperties.class)
    static class PropertiesOnly {
    }

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(PropertiesOnly.class)
            .withPropertyValues("app.jwt.expiry=1h", "app.jwt.issuer=expense-claims");

    @Test
    void startupFailsWhenTheSecretIsMissing() {
        contextRunner.withPropertyValues("app.jwt.secret=").run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause()
                    .hasMessageContaining("JWT_SECRET")
                    .hasMessageContaining("not configured");
        });
    }

    @Test
    void startupFailsWhenTheSecretIsTooShort() {
        String shortSecret = "only-31-bytes-long-xxxxxxxxxxxx";
        contextRunner.withPropertyValues("app.jwt.secret=" + shortSecret).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).rootCause()
                    .hasMessageContaining("too short")
                    .message().doesNotContain(shortSecret);
        });
    }

    @Test
    void startupSucceedsWithAValidSecret() {
        contextRunner.withPropertyValues("app.jwt.secret=" + VALID_SECRET).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(JwtProperties.class).expiry()).isEqualTo(Duration.ofHours(1));
        });
    }

    @Test
    void expiryMustBePositive() {
        assertThatThrownBy(() -> new JwtProperties(VALID_SECRET, Duration.ZERO, "expense-claims"))
                .hasMessageContaining("JWT_EXPIRY");
        assertThatThrownBy(() -> new JwtProperties(VALID_SECRET, Duration.ofMinutes(-5), "expense-claims"))
                .hasMessageContaining("JWT_EXPIRY");
    }

    @Test
    void toStringHidesTheSecret() {
        JwtProperties properties = new JwtProperties(VALID_SECRET, Duration.ofHours(1), "expense-claims");

        assertThat(properties.toString()).doesNotContain(VALID_SECRET).contains("<hidden>");
    }
}
