package com.example.expenseclaims.dto;

import com.example.expenseclaims.entity.ExpenseCategory;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plain unit tests for the validation rules on CreateExpenseClaimRequest: no Spring and no database.
 *
 * The validator uses a fixed clock, so "today" is always 2026-10-18 in these tests. Without it,
 * a test that runs exactly at midnight could see "tomorrow" turn into "today" and fail by chance.
 */
class CreateExpenseClaimRequestValidationTest {

    private static final ZoneId ZONE = ZoneId.of("UTC");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 18);

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void createValidator() {
        Clock fixedClock = Clock.fixed(TODAY.atStartOfDay(ZONE).plusHours(12).toInstant(), ZONE);
        validatorFactory = Validation.byDefaultProvider()
                .configure()
                .clockProvider(() -> fixedClock)
                .buildValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void validRequestHasNoViolations() {
        CreateExpenseClaimRequest request = new CreateExpenseClaimRequest(ExpenseCategory.TRAVEL,
                new BigDecimal("125.50"), TODAY.minusDays(3), "Taxi to client office");

        assertThat(violations(request)).isEmpty();
    }

    @Test
    void descriptionIsOptional() {
        CreateExpenseClaimRequest request = new CreateExpenseClaimRequest(ExpenseCategory.MEALS,
                new BigDecimal("20.00"), TODAY, null);

        assertThat(violations(request)).isEmpty();
    }

    @Test
    void missingCategoryIsRejected() {
        CreateExpenseClaimRequest request = new CreateExpenseClaimRequest(null,
                new BigDecimal("20.00"), TODAY, "Lunch");

        assertThat(violations(request)).containsExactly("category: category is required");
    }

    @Test
    void missingAmountIsRejected() {
        CreateExpenseClaimRequest request = new CreateExpenseClaimRequest(ExpenseCategory.MEALS,
                null, TODAY, "Lunch");

        assertThat(violations(request)).containsExactly("amount: amount is required");
    }

    @Test
    void zeroAmountIsRejected() {
        CreateExpenseClaimRequest request = new CreateExpenseClaimRequest(ExpenseCategory.MEALS,
                new BigDecimal("0.00"), TODAY, "Lunch");

        assertThat(violations(request)).containsExactly("amount: amount must be greater than zero");
    }

    @Test
    void negativeAmountIsRejected() {
        CreateExpenseClaimRequest request = new CreateExpenseClaimRequest(ExpenseCategory.MEALS,
                new BigDecimal("-5.00"), TODAY, "Lunch");

        assertThat(violations(request)).containsExactly("amount: amount must be greater than zero");
    }

    @Test
    void smallestAndLargestAmountsThatFitTheColumnAreAccepted() {
        CreateExpenseClaimRequest smallest = new CreateExpenseClaimRequest(ExpenseCategory.OTHER,
                new BigDecimal("0.01"), TODAY, null);
        // NUMERIC(12,2) holds at most 9,999,999,999.99.
        CreateExpenseClaimRequest largest = new CreateExpenseClaimRequest(ExpenseCategory.OTHER,
                new BigDecimal("9999999999.99"), TODAY, null);

        assertThat(violations(smallest)).isEmpty();
        assertThat(violations(largest)).isEmpty();
    }

    @Test
    void amountWithMoreThanTwoDecimalPlacesIsRejected() {
        CreateExpenseClaimRequest request = new CreateExpenseClaimRequest(ExpenseCategory.MEALS,
                new BigDecimal("12.345"), TODAY, "Lunch");

        assertThat(violations(request)).containsExactly(
                "amount: amount must have at most 10 digits before and 2 after the decimal point");
    }

    @Test
    void amountWithMoreThanTenDigitsBeforeTheDecimalPointIsRejected() {
        CreateExpenseClaimRequest request = new CreateExpenseClaimRequest(ExpenseCategory.MEALS,
                new BigDecimal("10000000000.00"), TODAY, "Too large");

        assertThat(violations(request)).containsExactly(
                "amount: amount must have at most 10 digits before and 2 after the decimal point");
    }

    @Test
    void missingExpenseDateIsRejected() {
        CreateExpenseClaimRequest request = new CreateExpenseClaimRequest(ExpenseCategory.MEALS,
                new BigDecimal("20.00"), null, "Lunch");

        assertThat(violations(request)).containsExactly("expenseDate: expenseDate is required");
    }

    @Test
    void futureExpenseDateIsRejected() {
        CreateExpenseClaimRequest request = new CreateExpenseClaimRequest(ExpenseCategory.MEALS,
                new BigDecimal("20.00"), TODAY.plusDays(1), "Lunch");

        assertThat(violations(request)).containsExactly("expenseDate: expenseDate cannot be in the future");
    }

    @Test
    void descriptionOfExactly500CharactersIsAccepted() {
        CreateExpenseClaimRequest request = new CreateExpenseClaimRequest(ExpenseCategory.MEALS,
                new BigDecimal("20.00"), TODAY, "a".repeat(500));

        assertThat(violations(request)).isEmpty();
    }

    @Test
    void descriptionLongerThan500CharactersIsRejected() {
        CreateExpenseClaimRequest request = new CreateExpenseClaimRequest(ExpenseCategory.MEALS,
                new BigDecimal("20.00"), TODAY, "a".repeat(501));

        assertThat(violations(request)).containsExactly("description: description must be at most 500 characters");
    }

    // Turns each violation into "field: message", which makes the assertions easy to read.
    private static List<String> violations(CreateExpenseClaimRequest request) {
        return validator.validate(request).stream()
                .map(CreateExpenseClaimRequestValidationTest::describe)
                .sorted()
                .toList();
    }

    private static String describe(ConstraintViolation<CreateExpenseClaimRequest> violation) {
        return violation.getPropertyPath() + ": " + violation.getMessage();
    }
}
