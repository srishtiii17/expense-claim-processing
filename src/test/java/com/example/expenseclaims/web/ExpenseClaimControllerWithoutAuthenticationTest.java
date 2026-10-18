package com.example.expenseclaims.web;

import com.example.expenseclaims.repository.ExpenseClaimRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Runs the claim endpoints with the real production identity provider (no test override).
 * Until JWT login exists (Checkpoint 3), every endpoint that needs an employee must answer 401,
 * and there must be no way to act as an employee by sending an id in the request.
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

    @Test
    void submittingAClaimRequiresAuthentication() throws Exception {
        long claimsBefore = expenseClaimRepository.count();
        String json = """
                {"category": "MEALS", "amount": 20.00, "expenseDate": "%s", "employeeId": 1}
                """.formatted(LocalDate.now().minusDays(1));

        mockMvc.perform(post("/api/claims").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
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
}
