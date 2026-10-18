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
   | `JWT_SECRET` | yes, to **run** the app (tests generate their own) | none: startup fails without it |
   | `JWT_EXPIRY` | no | `1h` |

   Generate `JWT_SECRET` once with a cryptographic random generator (PowerShell, prints nothing):
   ```powershell
   $bytes = New-Object byte[] 48
   [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
   [Environment]::SetEnvironmentVariable('JWT_SECRET', [Convert]::ToBase64String($bytes), 'User')
   ```
   Then open a **new** terminal or IDE (already-open ones keep the old environment).
   Changing `JWT_SECRET` invalidates every token issued with the old value.

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
| A2 | Managers approve or reject claims from their own department (**done, Checkpoint 4**), and see their whole department's claims, pending queue and monthly report (**done, Checkpoint 5**). Submission part superseded by C3 | `ClaimApprovalService`, `ClaimSpecifications.visibleTo` |
| A3 | Finance can read claims across all departments and the organisation-wide monthly report, read-only (**done, Checkpoint 5**); no write powers. Exact finance powers still an open question | `ClaimSpecifications.visibleTo`, `ExpenseReportService` |
| A4 | Expense date cannot be in the future; no maximum age. "Today" is the date in the **server's default time zone** (open question 5) | `@PastOrPresent` on `CreateExpenseClaimRequest.expenseDate` |
| A5 | Rejection requires a nonblank comment; approval comment optional; APPROVED and REJECTED are final; no resubmission or editing in v1 | `RejectClaimRequest`, `ClaimApprovalService` |
| A6 | Monthly totals are grouped by `expense_date` for the current calendar month, `[1st of month, 1st of next month)`, in the server's default time zone (the `APPROVED`-only part is confirmed by C2) | `ExpenseReportService`, `ClockConfig` |
| A7 | Categories are a fixed list: TRAVEL, MEALS, ACCOMMODATION, OFFICE_SUPPLIES, TRAINING, OTHER | `ExpenseCategory` enum + DB CHECK |
| A8 | Amount is stored as `NUMERIC(12,2)`; single currency; no upper limit | `ExpenseClaim.amount`, V1 migration |
| A9 | Only the `EMPLOYEE` role submits claims. C3 rules out managers; finance users are assumed not to submit either | `ExpenseClaimService.submitClaim` |
| A10 | A claim the caller may not see answers 404 (not 403), so claim ids cannot be probed. Since Checkpoint 5, `GET /api/claims/{id}` uses the same visibility as the list (employee: own; manager: own department; finance: all). The same 404 rule applies to a manager deciding a claim from another department | `ExpenseClaimService.getClaim`, `ClaimApprovalService` |
| A11 | Nobody may approve or reject their own claim (possible only for a claim submitted before a promotion to manager) | `ClaimApprovalService` |
| A12 | Any manager of the claim owner's department may decide it; there is no "assigned approver" or direct-report hierarchy (question 7) | `ClaimApprovalService` |
| A13 | A manager's pending-approval list leaves out their own SUBMITTED claims, because they may not decide them (A11). Those claims still appear in `GET /api/claims` | `ClaimSpecifications.pendingApprovalFor` |
| A14 | Filter ranges are inclusive at both ends; enum values are case-sensitive (`TRAVEL`, not `travel`); dates are ISO `yyyy-MM-dd` | `ClaimSearchCriteria`, `ExpenseClaimController` |

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

### Authentication status at the time of part 2 (superseded by Checkpoint 3)

At this point authentication was not implemented: every claim endpoint answered **HTTP 401** in the
running application, and tests supplied an identity from test-only code. Checkpoint 3 replaced
`UnauthenticatedCurrentEmployeeProvider` and the test-only provider with JWT authentication (see below).

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

(The first four points are resolved or changed by Checkpoint 3, below.)

---

## Checkpoint 3 — JWT authentication and role-based authorization

### Summary (2026-10-18)

Login with email and password returns a signed JWT. Every route except login requires
`Authorization: Bearer <token>`. Spring Security verifies the token, loads the employee and their
role from the database, and enforces the role rules. **Authentication is implemented; authorization
is implemented only for the endpoints that exist.** Manager/finance views and approvals are later
checkpoints (see "Not done yet").

No schema change and no new migration: `employees.password_hash VARCHAR(100)` already fits a
60-character BCrypt hash. There is no "disabled account" column, so only deleted accounts are handled.

### Dependencies added

| Dependency | Scope | Why |
|---|---|---|
| `spring-boot-starter-security-oauth2-resource-server` | compile | Spring Security, bearer-token filter, JWT encode/decode via Nimbus JOSE+JWT. Versions managed by the Spring Boot 4.1.1 parent (Spring Security 7.1.1) |
| `spring-boot-starter-security-oauth2-resource-server-test` | test | Spring Security test support for MockMvc |

No separate JWT library was added.

### Login

`POST /api/auth/login` (public). Body `{"email", "password"}`; both required, password at most 72
characters (BCrypt ignores anything after 72 bytes).

1. `EmployeeRepository.findByEmail` (exact match; see open questions).
2. `BCryptPasswordEncoder.matches(password, password_hash)`. When the email is unknown, the password is
   still checked against a dummy hash, so the response time does not reveal which emails exist.
3. Unknown email and wrong password give the **same** 401 body:
   `{"title": "Authentication failed", "status": 401, "detail": "Invalid email or password."}`.
4. Success: 200 with `Cache-Control: no-store` and
   `{"accessToken", "tokenType": "Bearer", "expiresIn" (seconds), "expiresAt", "employee": {id, fullName, departmentName}, "role"}`.

There is no registration endpoint and no seed user. To create a user, insert an `employees` row whose
`password_hash` is a BCrypt hash. Generate one with the project's own library (tested 2026-10-18):
```powershell
.\mvnw.cmd -q dependency:build-classpath "-Dmdep.outputFile=target\classpath.txt"
jshell --class-path (Get-Content target\classpath.txt -Raw).Trim()
jshell> new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("the-password")
```
The password is visible on screen while you type it, so do this privately.

### JWT design

| Item | Decision |
|---|---|
| Algorithm | HS256 (HMAC-SHA256) with `JWT_SECRET` (at least 32 bytes). The decoder accepts **only** HS256, so `alg: none` and other algorithms are rejected |
| Claims | `iss` = `expense-claims`, `sub` = employee id, `iat`, `exp`. Nothing else |
| Role | **Not in the token.** Loaded from the database on every request (`EmployeeJwtAuthenticationConverter`). A role change applies immediately, and a role added to a token is ignored (tested) |
| Lifetime | `JWT_EXPIRY`, default **1 hour**. No refresh tokens: after expiry the client logs in again |
| Checks on every request | Signature, `exp` present and not expired, `nbf`, `iss` matches, `sub` present and numeric, employee still exists. Clock tolerance: 60 seconds (Spring default) |
| Failures | Missing, malformed, expired, tampered, wrong issuer, unknown key, unsigned, deleted employee: all the same 401 with `WWW-Authenticate: Bearer` and detail "A valid bearer token is required." |
| Secret handling | `app.jwt.secret=${JWT_SECRET:}`. Missing or shorter than 32 bytes: the app **refuses to start** with a clear message that never includes the value (checked with a real startup). `JwtProperties`, `IssuedToken`, `LoginRequest` and `LoginResponse` hide secrets in `toString()` |
| Tests | `application-test.properties` sets `app.jwt.secret=${random.value}${random.value}`: a new random secret per test context, nothing committed |
| Logout / revocation | Not supported (stateless). A token stays valid until it expires unless the employee is deleted or `JWT_SECRET` changes |

### Spring Security configuration (`SecurityConfig`)

- Stateless: `SessionCreationPolicy.STATELESS`, no cookies, no form login, no HTTP Basic. CSRF protection
  is disabled because there are no cookies for a browser to send automatically.
- Spring Boot's default generated user and password are not created (checked in the test log).
- 401/403 raised inside the security filters are passed to `ApiExceptionHandler` through
  `SecurityProblemHandler`, so all errors use the same RFC 9457 format from one handler.
- Security checks run **before** the request body is read: an anonymous invalid body gets 401, and a
  manager's invalid body gets 403, not 400 (both tested).

### Identity

`JwtCurrentEmployeeProvider` replaced `UnauthenticatedCurrentEmployeeProvider`. It reads the employee id
from the verified token in the security context and nothing else. Controller and service were unchanged.

The test-only `TestCurrentEmployeeProvider` and `TestAuthenticationConfiguration` were **removed**. With
Spring Security active, a request without a valid token is refused before any controller runs, so
replacing the provider no longer had any effect. Tests now send real signed tokens instead.
`ExpenseClaimControllerWithoutAuthenticationTest.productionUsesOnlyTheJwtIdentityProvider` checks that
exactly one provider exists and that it is the JWT one.

### Permissions

| Endpoint | No/invalid token | EMPLOYEE | MANAGER | FINANCE |
|---|---|---|---|---|
| `POST /api/auth/login` | public | public | public | public |
| `POST /api/claims` | 401 | 201 (own claim) | 403 | 403 |
| `GET /api/claims/mine` | 401 | 200, own claims | 200, own claims (normally empty) | 200, own claims (normally empty) |
| `GET /api/claims/{id}` | 401 | 200 if own, else 404 | 200 if own, else 404 | 200 if own, else 404 |
| Any other path | 401 | 404 | 404 | 404 |

The submit rule is enforced twice: by the URL rule `hasRole("EMPLOYEE")` (403 before the body is read)
and by `ExpenseClaimService` (`ClaimSubmissionNotAllowedException`), so it also holds for future
non-HTTP callers.

### Changed test expectations (behaviour changed on purpose)

| Test | Before | Now | Why |
|---|---|---|---|
| `ExpenseClaimControllerTest.managerCannotSubmitAClaim` | 403, detail "Users with the MANAGER role cannot submit expense claims" | 403, detail "You do not have permission to perform this action." | Now rejected by the security rule before the service runs |
| `missingEmployeeReturns404AndSavesNothing` → `tokenForAMissingEmployeeReturns401AndSavesNothing` | 404 | 401 | A valid signature for a deleted employee is no longer a valid identity |

All other assertions are unchanged; only the identity source moved from the test provider to real tokens.
Mutation check: with the URL role rule removed, 4 tests in `RoleAuthorizationTest` failed as expected
(the rule was restored afterwards).

### Tests added

| Class | Tests | Covers |
|---|---|---|
| `web.AuthControllerTest` | 8 | Login returns a verifiable token (sub, iss, 1h lifetime, no role/email claims) that works on a protected route; `no-store`; no password or hash in the response; BCrypt storage; identical 401 for wrong password and unknown email; a plain-text stored password never works; blank fields and >72 chars give 400; other auth routes are protected |
| `security.JwtAuthenticationTest` | 12 | Valid token; missing, malformed, non-Bearer, expired, no `exp`, wrong issuer, non-numeric subject, unknown key, unsigned (`alg: none`), and edited-subject tokens all give the same generic 401; a token cannot read, list or create claims as another employee |
| `security.RoleAuthorizationTest` | 9 | Employee 201; manager and finance 403 with nothing saved; manager's invalid body still 403; all roles list own claims; unknown route with a valid token is 404; role claim in token ignored; role in body ignored; database role change applies to an existing token |
| `security.JwtPropertiesTest` | 5 | Startup fails without a secret or with a short one (value not in the message); valid secret works; expiry must be positive; `toString` hides the secret |
| `service.ExpenseClaimServiceTest` | 2 | Service refuses manager/finance submission and unknown employees without the HTTP layer |
| `web.ExpenseClaimControllerWithoutAuthenticationTest` | +4 (now 6) | Invalid body without a token is 401; `employeeId` query/header and role headers are not identities; unknown routes are protected; exactly one (JWT) identity provider |

### Test results (2026-10-18, JDK 25.0.4.1, PostgreSQL 18.6, `expense_claims_test`)

`.\mvnw.cmd clean test`: **86 tests, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS.**
The full console output contained no JWT, BCrypt hash, test password, database password or generated
default password.

### Not done yet / open policy questions

- **Managers** cannot yet see department claims, pending approvals or approve/reject (Checkpoints 4–5).
  Whether a manager sees the whole department or only direct reports is still open (question 7).
- **Finance** read-only access to all claims and the monthly report are not implemented (Checkpoint 5,
  A3). Finance currently has no extra powers.
- **Email matching is case-sensitive** (`Ravi@Example.com` does not match `ravi@example.com`), like the
  existing unique constraint. Decide whether to normalise emails.
- **No login rate limiting or lockout.** Repeated password guessing is only slowed by BCrypt.
- **No logout or token revocation**, no refresh tokens (question 11: tokens currently last 1 hour).
- **No disabled/locked account flag** in the schema; adding one would need a new migration.
- **No user management**: employees and their password hashes are created directly in the database.
- A request to the public login route that carries an invalid bearer token is still rejected with 401
  (Spring Security checks any token that is sent). Clients should not send a token to `/login`.

---

## Checkpoint 4 — manager approval and rejection workflow

### Summary (2026-10-18)

Managers can approve or reject `SUBMITTED` claims from their own department. Each decision changes the
claim's status and writes an `ApprovalComment` (the audit record) in the same transaction. Double or
conflicting decisions answer 409, including two decisions racing each other.

**No schema change and no new migration.** V1 already has everything needed: `expense_claims.version`
(`@Version`), the status CHECK (`SUBMITTED`, `APPROVED`, `REJECTED`), `approval_comments` with
`action` CHECK (`COMMENT`, `APPROVE`, `REJECT`), `author_id` and `claim_id` foreign keys, and nullable
`comment_text VARCHAR(1000)`. No new dependencies.

### Endpoints

| Method and path | Body | Success |
|---|---|---|
| `POST /api/claims/{claimId}/approve` | optional: `{"comment": "Looks good"}` (≤ 1000 chars) | 200 `ClaimDecisionResponse` |
| `POST /api/claims/{claimId}/reject` | required: `{"comment": "Receipt missing"}` (nonblank, ≤ 1000 chars) | 200 `ClaimDecisionResponse` |

`ClaimDecisionResponse` = `{"claim": ExpenseClaimResponse, "decision": ApprovalCommentResponse}`; the
decision shows `action`, `commentText`, `author` (id, name, department; no email) and `createdAt`.
Comments are trimmed; a blank approval comment is stored as `NULL`.

Errors, checked in this order:

| Status | When |
|---|---|
| 401 | No or invalid token |
| 403 | Caller is not `MANAGER` (security rule, before the body is read; the service checks again) |
| 400 | Invalid body: reject without a nonblank comment, comment over 1000 characters, malformed JSON, non-numeric id |
| 404 | Claim does not exist, **or** belongs to another department (same answer, so other departments' claim ids cannot be probed) |
| 403 | The claim is the manager's own ("You cannot approve or reject your own expense claim") |
| 409 | Claim is not `SUBMITTED` ("... is already APPROVED; only SUBMITTED claims can be approved or rejected") |
| 409 | Another decision on the same claim committed first (optimistic locking): "The claim was changed by another request at the same time. Reload it and try again." |

### State transitions

```
SUBMITTED --approve (MANAGER, same department, not own claim)--> APPROVED  (final)
SUBMITTED --reject  (same, nonblank reason)--------------------> REJECTED  (final)
```
Any other transition is refused with 409 before anything is written. Manager approval is final (C1):
there is no finance stage.

### Authorization rules

- **Role:** URL rule `POST /api/claims/*/approve` and `/reject` → `hasRole("MANAGER")` in `SecurityConfig`,
  plus the same check in `ClaimApprovalService` (`ClaimDecisionNotAllowedException`) for non-HTTP callers.
- **Department:** the claim owner's `department_id` must equal the manager's `department_id`. Both come
  from the database; the manager is the employee in the verified JWT.
- **Author:** the audit comment's author is always the logged-in manager. Request fields such as
  `authorId`, `managerId`, `employeeId`, `status` or `action` are ignored (tested).

### Atomicity and concurrency

- `ClaimApprovalService.approveClaim/rejectClaim` are `@Transactional`. Inside one transaction:
  1. load manager and claim, check role, department, ownership and status;
  2. `saveAndFlush(claim)`: runs `UPDATE expense_claims ... WHERE id = ? AND version = ?` immediately;
  3. `saveAndFlush(comment)`: inserts the audit comment.
  If step 2 or 3 fails, the whole transaction rolls back: no status change and no comment
  (tested by forcing step 3 to fail after step 2 ran).
- **Optimistic locking (existing `@Version`):** two decisions may both read `SUBMITTED` (version 0), but
  PostgreSQL makes the second `UPDATE` wait for the first, then it matches 0 rows because the version is
  now 1. Hibernate raises an optimistic locking failure, the losing transaction rolls back (its comment
  too) and `ApiExceptionHandler` answers 409 without internal details. If the requests do not overlap,
  the loser sees the new status and gets the "already APPROVED/REJECTED" 409 instead.
- Updating the claim before inserting the comment means a losing request never writes a comment first.
- Clients do not send a version number (no `If-Match`). A manager who decides based on an old screen
  still succeeds if the claim is still `SUBMITTED`; that is acceptable because nothing else can change a
  submitted claim yet.

### Tests added (20)

| Class | Tests | Covers |
|---|---|---|
| `web.ClaimApprovalControllerTest` | 15 | Approve (with comment, trimmed; without body); reject with reason; reject without reason (`{}`, null, "", blank, no body) is 400; comments over 1000 chars are 400; employee and finance get 403; non-manager with invalid body still 403; no token 401; other department's manager 404; own claim 403; missing claim 404; client-chosen author/status ignored; double approval 409; approve after reject 409; reject after approve 409. Every refusal checks the claim is still unchanged (status, version) and no comment was written; every success checks the single audit comment's action, author and text |
| `service.ClaimApprovalTransactionTest` | 4 | Committed transactions, not rolled back by the test. Rollback: comment insert forced to fail after the status `UPDATE` → status stays `SUBMITTED`, version 0, no comment, claim still decidable. Stale read: a decision based on an old version is refused by the version check. Race: 8 threads (2 managers, approve and reject) released together → exactly 1 success, 7 conflicts, 1 status change, 1 matching comment. Service-level role and reason checks. Deletes its own rows afterwards |
| `web.ApiExceptionHandlerTest` | 1 | Optimistic locking failure → 409 with a generic message (no class names, ids or SQL) |

The race test asserts the outcome (one winner), not which path each loser took: depending on timing,
losers are stopped by the status check or by the version check. The stale-read test exercises the
version check deterministically.

### Test results (2026-10-18, JDK 25.0.4.1, PostgreSQL 18.6, `expense_claims_test`)

`.\mvnw.cmd clean test`: **106 tests, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS**
(86 earlier + 15 + 4 + 1). Afterwards the test database had no leftover rows.

### Not done yet

- **Pending-approval list for managers** (claims of their department in `SUBMITTED`) and department
  claim views: Checkpoint 5. A manager currently needs the claim id to decide it.
- **Finance reporting** (monthly totals of `APPROVED` claims, C2): Checkpoint 5.
- **Reading the audit trail through the API:** there is no endpoint yet for an employee to read the
  rejection reason or for anyone to list a claim's comments. The decision response contains it, and it is
  stored in `approval_comments`.
- Plain comments (`CommentAction.COMMENT`) cannot be added through the API yet (question 4).
- No resubmission or editing of rejected claims (A5).
- No database-level "one decision per claim" constraint; it is enforced by the status check plus
  optimistic locking. A partial unique index on `approval_comments(claim_id)` for `APPROVE`/`REJECT`
  could be added in a new migration if the mentor wants a second safeguard.

(Checkpoint 5 below adds the pending-approval list and the report.)

---

## Checkpoint 5 — pending approvals, dynamic filtering, monthly aggregation

### Summary (2026-10-18)

Three read-only endpoints, plus a visibility change to `GET /api/claims/{id}`. **No schema change, no new
migration, no new dependencies.** Existing indexes cover the queries: `expense_claims(status, employee_id)`,
`expense_claims(employee_id)`, `expense_claims(expense_date)`, `employees(department_id)`.

### Endpoints

| Method and path | Roles | Returns |
|---|---|---|
| `GET /api/claims?status=&category=&minAmount=&maxAmount=&startDate=&endDate=&page=&size=` | any authenticated | `PageResponse<ExpenseClaimResponse>`, newest first |
| `GET /api/claims/pending-approvals?page=&size=` | MANAGER | `PageResponse<ExpenseClaimResponse>`, oldest first |
| `GET /api/reports/monthly-expenses-by-category` | MANAGER, FINANCE | `MonthlyCategoryReportResponse` |
| `GET /api/claims/{id}` (changed) | any authenticated | now uses the same visibility as the list |

`PageResponse` = `{"content": [...], "page", "size", "totalElements", "totalPages"}`. `page` starts at 0
(default 0), `size` 1–100 (default 20). Out-of-range values give 400.

### Visibility (who sees which claims)

One rule, `ClaimSpecifications.visibleTo(caller)`, used by the list, the detail endpoint, and as the base of
every search:

| Caller role | Sees |
|---|---|
| EMPLOYEE | own claims only |
| MANAGER | every claim whose owner is in the manager's department (including the manager's own) |
| FINANCE | all claims, all departments (read-only: finance has no write endpoints) |

The caller, their role and their department come from the database row of the employee in the verified JWT.
There is no `employeeId` or `departmentId` parameter; if a client sends one, it is ignored (tested).
Filters are added with `AND`, so they can only narrow what the caller may see.

### Dynamic filtering

- `ClaimSearchCriteria` holds the six optional filters. Its constructor rejects `minAmount > maxAmount` and
  `startDate > endDate` (`InvalidFilterException` → 400 "Invalid request" with a plain message).
  Equal bounds are allowed. Ranges are inclusive (A14).
- `ClaimSpecifications.matching(criteria)` builds one JPA Criteria predicate with only the filters that
  were given. `ExpenseClaimRepository` extends `JpaSpecificationExecutor`, so one `findAll(spec, pageable)`
  covers every combination; there are no per-combination repository methods. Values are bind parameters.
- Invalid values (`status=PENDING`, `category=travel`, `startDate=2026-13-01`, `minAmount=abc`, `size=0`)
  are rejected by Spring's type conversion or method validation and answered by the existing
  `ApiExceptionHandler` as 400 problem details without class names.

### Pending approvals

`ClaimSpecifications.pendingApprovalFor(manager)`: `status = SUBMITTED`, owner's department = manager's
department, owner ≠ manager (A13). Sorted oldest first (`createdAt`, then `id`). Deciding a claim removes it
from the list (tested). Approve/reject rules from Checkpoint 4 are unchanged.

### N+1 prevention

`ExpenseClaimRepository.findAll(Specification, Pageable)` is overridden with
`@EntityGraph(attributePaths = {"employee", "employee.department"})`, so the claims, their owners and
departments come back in one SQL query (the count query is unaffected). A test clears the persistence
context, enables Hibernate statistics and checks that listing 6 claims from 3 owners in 2 departments takes
at most 2 statements (caller lookup + one claims query). With the entity graph removed, this test fails
(checked).

### Monthly aggregation

- JPQL aggregate queries in `ExpenseClaimRepository` (`sumApprovedAmountsByCategory` and
  `...ForDepartment`): `SELECT new CategoryTotal(c.category, SUM(c.amount)) ... WHERE status = APPROVED AND
  expenseDate >= :start AND expenseDate < :end [AND c.employee.department.id = :departmentId]
  GROUP BY c.category ORDER BY c.category`. Hibernate translates this to SQL `SUM ... GROUP BY`, so
  PostgreSQL does the aggregation and returns one row per category. All values are bind parameters.
  (JPQL was chosen over a native query because it is checked against the entity model at startup and uses
  the enum types directly.)
- Period: `YearMonth.now(clock)` → `[1st of month, 1st of next month)`. `Clock` is a bean (`ClockConfig`,
  system default zone, A6) so tests can fix "today".
- FINANCE: `scope = "ORGANIZATION"`, `departmentName = null`. MANAGER: `scope = "DEPARTMENT"`, own department
  only. EMPLOYEE: 403 (URL rule + service check).
- Categories with no approved claims are left out; no data gives `"totals": []` and `"grandTotal": 0.00`.
  `grandTotal` adds up the (at most six) category totals returned by the database.
- A test checks the report runs at most 2 statements and loads **zero** `ExpenseClaim` entities.

Example response:
```json
{"month": "2025-02", "periodStart": "2025-02-01", "periodEndExclusive": "2025-03-01",
 "scope": "DEPARTMENT", "departmentName": "Sales",
 "totals": [{"category": "MEALS", "total": 30.00}, {"category": "TRAVEL", "total": 150.25}],
 "grandTotal": 180.25}
```

### Tests added (40)

| Class | Tests | Covers |
|---|---|---|
| `web.ClaimSearchControllerTest` | 27 | No-filter visibility for employee, both managers and finance; DTO shape; each filter alone (status, category, min/max amount inclusive, start/end date inclusive); all filters combined (as finance and as a manager who sees none); amount + date ranges; equal bounds; filters never widen employee or manager views; `employeeId`/`departmentId` parameters ignored; min > max and start > end → 400 with message; invalid enums (wrong value, wrong case), malformed dates/amounts, invalid paging → 400 without internal details; paging; no token → 401; `GET /{id}` for manager (own department 200, other 404), finance (any 200), employee (colleague 404); N+1 statement count |
| `web.PendingApprovalsControllerTest` | 6 | Only SUBMITTED claims of the manager's department, oldest first, excluding approved/rejected, other departments and the manager's own claim; empty department → empty page; decided claims leave the list; paging and size limit; `departmentId` ignored; employee and finance 403, no token 401 |
| `web.MonthlyReportControllerTest` | 7 | Fixed clock 2025-02-15: finance organisation-wide totals per category; manager department totals (two departments); first and last day of the month included, previous/next month excluded, SUBMITTED/REJECTED excluded; empty month; manager with no approved claims; employee 403, no token 401; `departmentId`/`scope` parameters ignored; aggregation in the database (≤ 2 statements, 0 claim entities loaded). Changing `< :end` to `<= :end` makes 3 of these fail (checked) |

### Test results (2026-10-18, JDK 25.0.4.1, PostgreSQL 18.6, `expense_claims_test`)

`.\mvnw.cmd test` (after `.\mvnw.cmd clean`): **146 tests, 0 failures, 0 errors, 0 skipped — BUILD SUCCESS**
(106 earlier + 27 + 6 + 7). No existing test was changed.

### Not done / open questions

- **Month and time zone:** only the current month, in the server's default time zone (question 5). No
  `?month=` parameter yet.
- **Manager scope:** whole department, not direct reports (question 7).
- **Report viewers:** managers (own department) and finance (all) can see it; employees cannot (question 8).
- **No sorting parameter:** list order is fixed (newest first; pending oldest first).
- The `departmentName` field is `null` in the organisation-wide report (Jackson writes `"departmentName": null`).
- Audit trail reading, plain comments and resubmission remain open (see Checkpoint 4).

---

## Open mentor questions

1. ~~Approval stages~~ answered by C1. Still open: finance's exact powers. Read-only reporting only, or anything else? (A3)
2. Resubmission or editing of claims (current assumption: A5).
3. ~~Who approves a manager's own claim?~~ Answered by C3: managers do not submit claims.
4. Who may add plain comments (owner, approvers, finance)?
5. Time zone used for "today" and "current month" (server default vs company zone).
6. Category list and any amount limit or currency (A7, A8).
7. Does a manager see the whole department or only direct reports?
8. Who may view the monthly report? (Current: finance organisation-wide, managers their own department,
   employees not at all.)
9. Is a real PostgreSQL test database acceptable, or are H2 tests expected?
10. Are JDK 25 and Spring Boot 4.1.1 acceptable?
11. How are users created (seed data vs registration), token lifetime, refresh tokens? (Current: created
    directly in the database, 1-hour tokens, no refresh tokens.)
13. Should finance users be able to submit their own expense claims? (Current assumption A9: no.)
14. Should login emails be case-insensitive, and is login rate limiting or account lockout required?
15. May any manager of a department decide its claims, or only an assigned approver (A12)? Should a
    manager be able to undo a decision?
16. Should approving require a comment too, or only rejecting (current: only rejecting)?
12. Required base package name (currently `com.example.expenseclaims`)?
