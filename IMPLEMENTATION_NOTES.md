# Implementation Notes

This file records **verified** facts about the code, the business-rule assumptions in use,
and open questions. It is updated at the end of every checkpoint.

---

## Environment and versions

| Item | Value | Verified how |
|---|---|---|
| Spring Boot | 4.1.1 (latest stable on start.spring.io, 2026-10-14) | start.spring.io metadata |
| Java target | 25 (`<java.version>25</java.version>`, compiled with `--release 25`) | Maven compiler output |
| Supported Java range for Boot 4.1.1 | 17 to 26 | Spring Boot 4.1.1 system requirements page |
| Build tool | Maven Wrapper 3.3.4, downloads Maven 3.10.0 | `.mvn/wrapper/maven-wrapper.properties`, `~/.m2/wrapper/dists` |
| Database | PostgreSQL 18.6 on `localhost:5432` (14.24 also installed on port 5433, unused) | `psql --version`, `postgresql.conf`, `netstat` |

**JDK (updated 2026-10-16):** Temurin JDK 25.0.4.1 (LTS) is installed and active
(`JAVA_HOME` = machine variable, `java -version` and Maven both report 25.0.4.1).
This resolves the earlier JDK 27 issue.

**Environment variables (checked 2026-10-16, values not printed):** `DB_USERNAME` and
`DB_PASSWORD` are set as user variables. `DB_URL` and `TEST_DB_URL` are unset, so the
defaults are used (port 5432, PostgreSQL 18).

---

## Local setup

1. Install JDK 25 (for example `winget install EclipseAdoptium.Temurin.25.JDK`), set
   `JAVA_HOME` to it, and put `%JAVA_HOME%\bin` before the Oracle `javapath` entry in `PATH`.
2. Create the database user and databases (run in `psql` as the `postgres` superuser):
   ```sql
   CREATE ROLE expense_app WITH LOGIN PASSWORD '<choose-a-password>';
   CREATE DATABASE expense_claims      OWNER expense_app;
   CREATE DATABASE expense_claims_test OWNER expense_app;
   ```
3. Set environment variables (Windows user variables or IDE run configuration):

   | Variable | Required | Default |
   |---|---|---|
   | `DB_USERNAME` | yes | none |
   | `DB_PASSWORD` | yes | none |
   | `DB_URL` | no | `jdbc:postgresql://localhost:5432/expense_claims` |
   | `TEST_DB_URL` | no | `jdbc:postgresql://localhost:5432/expense_claims_test` |

4. Run the tests: `.\mvnw.cmd test`

**Credentials:** no password or secret is stored in any file. `.gitignore` also excludes
`.env` files and `application-local.properties` in case one is created locally.

---

## Mentor clarifications (confirmed 2026-10-16)

| # | Clarification | Replaces | Code impact |
|---|---|---|---|
| C1 | **Manager approval is the final approval.** A claim goes `SUBMITTED → APPROVED` or `SUBMITTED → REJECTED`, decided by the manager. There is no second (finance) approval stage. | A1 | **Done (2026-10-16):** `ClaimStatus` now has only `SUBMITTED`, `APPROVED`, `REJECTED` (Javadoc updated); the `ck_expense_claims_status` CHECK in `V1__create_initial_tables.sql` allows only those three values; `ExpenseClaimRepositoryTest.versionIncreasesWhenClaimIsUpdated` uses `APPROVED`. V1 was edited in place because it had never been applied to any database (every connection attempt failed authentication), so there is no Flyway checksum conflict. |
| C2 | **Monthly totals include `APPROVED` claims only.** | Confirms the status part of A6 | None; matches A6. |
| C3 | **Managers do not need to submit their own claims.** | A2 (submission part); resolves open question 3 | **Done (Checkpoint 2 part 2):** `ExpenseClaimService.submitClaim` allows only the `EMPLOYEE` role; `MANAGER` (and `FINANCE`, see A9) get HTTP 403. |

## Business-rule assumptions (not yet confirmed by mentor)

| # | Assumption | Where it shows up in code |
|---|---|---|
| ~~A1~~ | Withdrawn. Replaced by C1 (single-stage approval); no code still uses it. | — |
| A2 | Managers view their department's claims and approve or reject them (submission part superseded by C3) | Checkpoints 3–4 |
| A3 | Finance can view claims across all departments (read-only, because of C1; exact finance powers still open) | Checkpoints 3–5 |
| A4 | Expense date cannot be in the future; no maximum age. "Today" is the date in the **server's default time zone** (open question 5) | `@PastOrPresent` on `CreateExpenseClaimRequest.expenseDate` |
| A5 | Rejection requires a comment; rejected claims are final; no resubmission in v1 | Checkpoint 4 |
| A6 | Monthly totals are grouped by `expense_date` for the current calendar month (the `APPROVED`-only part is confirmed by C2) | Checkpoint 5 |
| A7 | Categories are a fixed list: TRAVEL, MEALS, ACCOMMODATION, OFFICE_SUPPLIES, TRAINING, OTHER | `ExpenseCategory` enum + DB CHECK |
| A8 | Amount is stored as `NUMERIC(12,2)`; single currency; no upper limit | `ExpenseClaim.amount`, V1 migration |
| A9 | Only the `EMPLOYEE` role submits claims. C3 rules out managers; finance users are assumed not to submit either | `ExpenseClaimService.submitClaim` |
| A10 | Until Checkpoint 3 adds roles, a claim is visible only to its owner. Another employee's claim answers 404 (not 403), so claim ids cannot be probed | `ExpenseClaimService.getClaim` |

---

## Checkpoint 1 — project skeleton, schema, entities, DTO foundations

### What exists (verified by reading the code and compiling it)

**Schema** (`V1__create_initial_tables.sql`): tables `departments`, `employees`,
`expense_claims`, `approval_comments`, with foreign keys, a unique constraint on
department name and employee email, CHECK constraints for enum values and `amount > 0`,
and indexes on `employees(department_id)`, `expense_claims(employee_id)`,
`expense_claims(status, employee_id)`, `expense_claims(expense_date)` and
`approval_comments(claim_id)`.

**Entities:** `Department`, `Employee` (many-to-one department), `ExpenseClaim`
(many-to-one employee, `@Version` for optimistic locking, timestamps set by
`@PrePersist`/`@PreUpdate`), `ApprovalComment` (many-to-one claim and author).
All relationships are `LAZY`. Comments are not mapped as a list on `ExpenseClaim`;
they are loaded through `ApprovalCommentRepository`.

**Configuration:** `ddl-auto=validate` (Hibernate checks entities match Flyway's tables
but never changes the schema); `open-in-view=false`.

**DTOs (records):** `EmployeeSummaryResponse` (no email or password hash),
`ExpenseClaimResponse`, `ApprovalCommentResponse`, `CreateExpenseClaimRequest`
(no owner, id or status fields). Validation annotations were added in Checkpoint 2.

**Not included on purpose:** controllers, services, validation and security dependencies.
They belong to Checkpoints 2–3.

### Test results (2026-10-15, run on JDK 27 — outside the supported range)

| Test class | Result |
|---|---|
| `DtoMappingTest` (4 tests, no database) | **Passed** |
| `ExpenseClaimsApplicationTests` (1) | **Error** — no database credentials |
| `EmployeeRepositoryTest` (3) | **Error** — no database credentials |
| `ExpenseClaimRepositoryTest` (4) | **Error** — no database credentials |
| `ApprovalCommentRepositoryTest` (1) | **Error** — no database credentials |

Error message: `FATAL: password authentication failed for user "${DB_USERNAME}"`.
The Flyway migration, Hibernate schema validation and repository tests have therefore
**not been verified yet**.

### Test results (2026-10-16, JDK 25.0.4.1, PostgreSQL 18 on port 5432)

| Test class | Result |
|---|---|
| `DtoMappingTest` (4 tests, no database) | **Passed** |
| `ExpenseClaimsApplicationTests` and the 3 repository test classes | **Error**: `FATAL: password authentication failed for user "expense_app"` |

Diagnosis: the username now resolves correctly. A direct `psql` login with the same
environment variables fails too, so this is not a Spring configuration problem. The
PostgreSQL 18 server log shows no "role does not exist" detail, so the role exists and the
password in `DB_PASSWORD` does not match the role's password. (The unused PostgreSQL 14 server
on 5433 has no `expense_app` role, as expected.) The password has no stray whitespace or
quotes. Fix: make the two match, either with `ALTER ROLE expense_app PASSWORD '...'` as
`postgres` or by correcting the user variable and opening a new terminal. Database-backed tests
are **still unverified**.

**Resolved (2026-10-16):** the user corrected the `expense_app` password mismatch outside this
project. No credential was written to any file.

### Final test results (2026-10-16 22:57, after the C1 correction)

Full suite (`.\mvnw.cmd test`) on JDK 25.0.4.1 against PostgreSQL 18.6 on port 5432
(test profile database `expense_claims_test`; the development database `expense_claims` is not
yet migrated, because no test uses it and the application has not been started against it):
**13 tests, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS.** Verified from
`target/surefire-reports` (all five report files dated 2026-10-16 22:57).

| Test class | Tests | Result |
|---|---|---|
| `DtoMappingTest` (no database) | 4 | **Passed** |
| `ExpenseClaimsApplicationTests` | 1 | **Passed** |
| `EmployeeRepositoryTest` | 3 | **Passed** |
| `ExpenseClaimRepositoryTest` | 4 | **Passed** |
| `ApprovalCommentRepositoryTest` | 1 | **Passed** |

PostgreSQL/Flyway evidence in the test output:
- `Successfully applied 1 migration to schema` (the edited V1, with the three-value status CHECK),
  then `Successfully validated 1 migration` and `Schema "public" is up to date` on later contexts.
- Hibernate `ddl-auto=validate` passed, so the entities match the Flyway-created tables.
- Database CHECK constraints confirmed by tests: `amount > 0` (`databaseRejectsZeroAmount`) and the
  unique employee email (`EmployeeRepositoryTest`). `versionIncreasesWhenClaimIsUpdated` confirms
  that `APPROVED` is accepted by the new status CHECK and that `@Version` increments.

Not covered by a test: there is no test that tries to insert a removed or invalid status value directly.

### Known limitations / notes
- When `DB_USERNAME` is unset, Spring passes the placeholder text itself as the username,
  so the error message is unusual (see above). Startup still fails, which is the safe outcome.
- Maven prints two `401` warnings for repositories declared inside Flyway's own published
  POMs (`flyway-community-db-support`, Red Gate `MavenFeed`). Dependencies resolve from
  Maven Central; the build is not affected.
- Entities do not override `equals`/`hashCode`; they are compared by id where needed.

---

## Checkpoint 2 (part 1) — request validation

### What changed (2026-10-18)

- `pom.xml`: added `spring-boot-starter-validation` (Hibernate Validator; version managed by the
  Spring Boot parent).
- `CreateExpenseClaimRequest`: validation annotations that mirror the V1 columns. No schema change;
  V1 was not touched.

| Field | Annotations | Matches database |
|---|---|---|
| `category` | `@NotNull` | `category ... NOT NULL` + category CHECK |
| `amount` | `@NotNull`, `@Positive`, `@Digits(integer = 10, fraction = 2)` | `NUMERIC(12, 2) NOT NULL`, `CHECK (amount > 0)` |
| `expenseDate` | `@NotNull`, `@PastOrPresent` | `expense_date DATE NOT NULL`; the "no future date" rule exists only in Java (A4) |
| `description` | `@Size(max = 500)` (optional, `null` allowed) | `VARCHAR(500)`, nullable |

- New test class `CreateExpenseClaimRequestValidationTest` (13 plain unit tests, no Spring, no
  database). It uses a fixed clock (2026-10-18, UTC) so the future-date test cannot fail by chance
  at midnight.

**Update (part 2):** `ExpenseClaimController` now receives the request with `@Valid`, and
`ApiExceptionHandler` turns violations into an HTTP 400 response.

### Test results (2026-10-18, JDK 25.0.4.1, PostgreSQL 18.6)

Full suite (`.\mvnw.cmd test`): **26 tests, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS.**
13 new validation tests + 13 Checkpoint 1 tests. Flyway: `Successfully validated 1 migration`,
`Schema "public" is up to date` (V1 unchanged, no new migration).

### Notes from this step

- **Stale environment variable:** the first full-suite run failed with `password authentication failed
  for user "expense_app"`. The Claude Code session had started with an old copy of `DB_PASSWORD`; the
  Windows user variable was already correct (direct `psql` login succeeded). A terminal or IDE opened
  before a variable is changed keeps the old value. Fix: restart it.
- **Stale incremental compile:** once, `target/classes` held a `CreateExpenseClaimRequest` without its
  constructor and accessors, so test compilation failed with "cannot find symbol". `.\mvnw.cmd clean`
  fixed it; the root cause was not identified. If an impossible "cannot find symbol" appears, run
  `.\mvnw.cmd clean test`.
- `@Size` counts Java `char`s, so an emoji counts as 2 while PostgreSQL counts it as 1. Java is
  therefore slightly stricter than the column, never looser.

---

## Checkpoint 2 (part 2) — claim submission and retrieval

### Authentication status — read this first

**Authentication and authorization are NOT implemented. They are scheduled for Checkpoint 3.**
The production application has no login, so it cannot know who is calling. Instead of trusting an
employee id sent by the caller, every claim endpoint currently answers **HTTP 401** in the running
application. The endpoints are fully exercised by tests, which supply an identity from test-only code.

### What was implemented (2026-10-18)

| Layer | Class | Responsibility |
|---|---|---|
| Identity boundary | `security.CurrentEmployeeProvider` (interface) | "Which employee is calling?" The only place the app learns the caller's identity |
| | `security.UnauthenticatedCurrentEmployeeProvider` (`@Component`) | Production implementation until Checkpoint 3: always throws `AuthenticationRequiredException` (401) |
| | `security.AuthenticationRequiredException` | Mapped to 401 |
| Service | `service.ExpenseClaimService` | Submit (role check, save as `SUBMITTED`), get one claim, list own claims. `@Transactional(readOnly = true)` by default, `@Transactional` for submit. Builds DTOs inside the transaction (lazy loading, open-in-view off) |
| | `service.ClaimNotFoundException`, `EmployeeNotFoundException`, `ClaimSubmissionNotAllowedException` | Expected failures, mapped to 404 / 404 / 403 |
| Web | `web.ExpenseClaimController` | HTTP only: resolves the caller through `CurrentEmployeeProvider`, calls the service, sets status codes |
| | `web.ApiExceptionHandler` (`@RestControllerAdvice`) | The only exception handler. Returns RFC 9457 problem details (`application/problem+json`) |
| Repository | `ExpenseClaimRepository.findByEmployeeIdOrderByCreatedAtDescIdDesc` | "My claims", newest first (uses `idx_expense_claims_employee_id`) |

No schema change and no new migration: V1 already had every column needed.
No new dependencies: validation, Spring MVC, Jackson and MockMvc were already on the classpath.

### API

| Method and path | Success | Errors |
|---|---|---|
| `POST /api/claims` | 201 Created, `Location: /api/claims/{id}`, body `ExpenseClaimResponse` | 400 invalid body; 401 no identity; 403 caller is not `EMPLOYEE`; 404 caller's employee record missing |
| `GET /api/claims/mine` | 200, array of `ExpenseClaimResponse`, newest first (empty array if none) | 401; 404 employee missing |
| `GET /api/claims/{claimId}` | 200, `ExpenseClaimResponse` | 400 non-numeric id; 401; 404 missing **or** not the caller's claim |

### Design decisions

- **The service takes the employee id as a parameter.** It does not know about HTTP or login, so
  Checkpoint 3 only needs a new `CurrentEmployeeProvider` (for example one that reads the JWT
  subject); controller and service stay unchanged.
- **The owner is never taken from the request.** `CreateExpenseClaimRequest` has no owner or status
  field. Extra JSON properties such as `employeeId` or `status` are ignored (tested).
- **Test identity lives only in `src/test`.** `TestCurrentEmployeeProvider` is not a `@Component`;
  tests opt in with `@Import(TestAuthenticationConfiguration.class)`, which registers it as `@Primary`.
  There is no header, profile or flag in production code that switches identity on.
- **404 for other people's claims**, so a caller cannot tell "does not exist" from "not yours" (A10).
- **Amounts are normalised to 2 decimal places** (`setScale(2)`, which never rounds because `@Digits`
  already limits the scale), so a response shows `125.50` exactly as stored.
- **Errors never contain stack traces, class names or SQL.** Unexpected exceptions are logged on the
  server and answered with a generic 500.

### Tests added

| Class | Tests | Covers |
|---|---|---|
| `web.ExpenseClaimControllerTest` | 17 | 201 + Location + `SUBMITTED`; row saved in PostgreSQL for the caller; extra `employeeId`/`status` ignored; no email/password/version in responses; manager gets 403; invalid fields (400 with per-field messages, nothing saved); too many decimals; unknown category, malformed JSON and non-numeric id (400 without internal details); read own claim; "mine" filtered and newest first; empty list; missing claim 404; other employee's claim 404; missing employee 404 and nothing saved; signed-out 401 |
| `web.ExpenseClaimControllerWithoutAuthenticationTest` | 2 | Production provider: submit and read answer 401, and nothing is saved even when the body contains `employeeId` |
| `repository.ExpenseClaimRepositoryTest` | +1 | New query returns only that employee's claims, newest first |

All tests use the `test` profile and `expense_claims_test`, and roll back their data.

### Test results (2026-10-18, JDK 25.0.4.1, PostgreSQL 18.6)

`.\mvnw.cmd clean test`: **46 tests, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS**
(26 earlier + 17 controller + 2 unauthenticated + 1 repository).

### Remaining limitations

- **No authentication or authorization (Checkpoint 3).** In the running application every claim
  endpoint answers 401.
- Managers and finance cannot view other employees' claims yet (A2, A3, A10). Approval is Checkpoint 4.
- Validation runs before the identity check, so an unauthenticated request with an invalid body gets
  400, not 401. Spring Security (Checkpoint 3) checks identity first, which will fix this order.
- `EmployeeNotFoundException` answers 404. Once tokens exist, a token for a deleted employee may be
  better answered with 401; decide in Checkpoint 3.
- "My claims" has no paging or filtering yet.
- MockMvc exercises the full Spring MVC stack without starting a real HTTP server.

---

## Open mentor questions

1. ~~Approval stages~~ answered by C1. Still open: finance's exact powers. Read-only reporting only, or anything else? (A3)
2. Resubmission or editing of claims (current assumption: A5).
3. ~~Who approves a manager's own claim?~~ Answered by C3: managers do not submit claims.
4. Who may add plain comments (owner, approvers, finance)?
5. Time zone used for "today" and "current month" (server default vs company zone).
6. Category list and any amount limit or currency (A7, A8).
7. Does a manager see the whole department or only direct reports?
8. Who may view the monthly report (assumed: finance)?
9. Is a real PostgreSQL test database acceptable, or are H2 tests expected?
10. Are JDK 25 and Spring Boot 4.1.1 acceptable?
11. How are users created (seed data vs registration), token lifetime, refresh tokens?
12. Required base package name (currently `com.example.expenseclaims`)?
