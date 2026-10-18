package com.example.expenseclaims.web;

import com.example.expenseclaims.repository.ExpenseClaimRepository;
import com.example.expenseclaims.security.CurrentEmployeeProvider;
import com.example.expenseclaims.security.JwtCurrentEmployeeProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs the claim endpoints exactly as the production application is configured, with no token.
 * Every protected endpoint must answer 401, and no request field, parameter or header can be used
 * to act as an employee instead of a verified token.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ExpenseClaimControllerWithoutAuthenticationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExpenseClaimRepository expenseClaimRepository;

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void submittingAClaimRequiresAuthentication() throws Exception {
        long claimsBefore = expenseClaimRepository.count();
        String json = """
                {"category": "MEALS", "amount": 20.00, "expenseDate": "%s", "employeeId": 1}
                """.formatted(LocalDate.now().minusDays(1));

        mockMvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(jsonPath("$.title").value("Authentication required"));

        assertThat(expenseClaimRepository.count()).isEqualTo(claimsBefore);
    }

    @Test
    void readingClaimsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/claims/mine"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/claims/{id}", 1))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidBodyWithoutTokenGets401Not400() throws Exception {
        // The security check runs before the body is validated, so an anonymous caller
        // cannot probe the validation rules.
        mockMvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON).content("{\"amount\": -1}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void employeeIdInQueryParametersOrHeadersIsNotAnIdentity() throws Exception {
        mockMvc.perform(get("/api/claims/mine").param("employeeId", "1"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/claims/mine").header("X-Employee-Id", "1").header("X-User-Role", "FINANCE"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void unknownRoutesAreProtectedByDefault() throws Exception {
        mockMvc.perform(get("/api/reports/monthly"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void productionUsesOnlyTheJwtIdentityProvider() {
        // No test-only or fallback provider can be active: the one and only provider reads the verified JWT.
        Map<String, CurrentEmployeeProvider> providers = applicationContext.getBeansOfType(CurrentEmployeeProvider.class);

        assertThat(providers).hasSize(1);
        assertThat(providers.values().iterator().next()).isInstanceOf(JwtCurrentEmployeeProvider.class);
    }
}
