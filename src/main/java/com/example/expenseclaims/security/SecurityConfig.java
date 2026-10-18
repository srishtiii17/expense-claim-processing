package com.example.expenseclaims.security;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;

/**
 * Security rules for the whole application.
 *
 * - Stateless: no HTTP session and no cookies. Every request must carry "Authorization: Bearer <token>".
 * - Public: only POST /api/auth/login (and Spring's internal /error page).
 * - Everything else requires a valid token. New routes are protected automatically.
 * - Role rules: only EMPLOYEE may submit claims (mentor clarification C3, assumption A9); only
 *   MANAGER may approve or reject them (Checkpoint 4) or list pending approvals; only MANAGER and
 *   FINANCE may read reports (Checkpoint 5). These checks run in the security filter, before the
 *   request is read or validated. The services check the same rules again.
 * - Which claims a caller may READ (own / department / all) is decided per role in
 *   ClaimSpecifications.visibleTo, because it depends on data, not only on the URL.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   EmployeeJwtAuthenticationConverter jwtAuthenticationConverter,
                                                   SecurityProblemHandler problemHandler) throws Exception {
        http
                // CSRF attacks rely on cookies the browser sends automatically. This API uses no cookies
                // or sessions, only a bearer token the client adds itself, so CSRF protection is not needed.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/claims").hasRole("EMPLOYEE")
                        .requestMatchers(HttpMethod.POST, "/api/claims/*/approve", "/api/claims/*/reject")
                        .hasRole("MANAGER")
                        .requestMatchers(HttpMethod.GET, "/api/claims/pending-approvals").hasRole("MANAGER")
                        .requestMatchers("/api/reports/**").hasAnyRole("MANAGER", "FINANCE")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler));
        return http.build();
    }

    // BCrypt stores a salted, deliberately slow hash; a plain-text password is never stored or compared.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtEncoder jwtEncoder(JwtProperties jwtProperties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(signingKey(jwtProperties)));
    }

    /**
     * Verifies every incoming token before anything trusts it:
     * - signature: HMAC-SHA256 with our secret only (tokens using "none" or any other algorithm fail)
     * - exp and nbf: not expired, not used too early (60 seconds clock tolerance)
     * - iss: must equal app.jwt.issuer
     * - exp and sub: must be present
     */
    @Bean
    public JwtDecoder jwtDecoder(JwtProperties jwtProperties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(signingKey(jwtProperties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(jwtProperties.issuer()),
                new JwtClaimValidator<Instant>(JwtClaimNames.EXP, Objects::nonNull),
                new JwtClaimValidator<String>(JwtClaimNames.SUB, subject -> subject != null && !subject.isBlank())));
        return decoder;
    }

    private static SecretKey signingKey(JwtProperties jwtProperties) {
        return new SecretKeySpec(jwtProperties.secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
