# Corporate Expense Claim Processing

A REST backend where employees submit expense claims, managers approve or reject the claims of
their own department, and finance staff read claims and monthly reports across the organisation.

It is a **backend-only** project: there is no frontend. You use it through HTTP requests, for example
with PowerShell's `Invoke-RestMethod`, curl or Postman.

Design decisions, assumptions and the history of each checkpoint are in
[`IMPLEMENTATION_NOTES.md`](IMPLEMENTATION_NOTES.md).

---

## 1. Project Overview

Main capabilities:

- Log in with email and password and receive a JWT (JSON Web Token).
- Employees submit expense claims, which are validated before saving.
- Managers see a queue of their department's pending claims and approve or reject them. Each decision is
  stored with an audit comment.
- Every role can list and filter the claims it is allowed to see, page by page.
- Managers and finance get a monthly report of approved totals per category, calculated in the database.

The approval workflow is **single-stage**, as confirmed by the mentor: `SUBMITTED → APPROVED` or
`SUBMITTED → REJECTED`, and the manager's decision is final.

---

## 2. Technology Stack

| Technology | Version / detail | Used for |
|---|---|---|
| Java | 25 (`<java.version>25</java.version>` in `pom.xml`) | Language |
| Spring Boot | 4.1.1 | Application framework |
| Spring Web MVC | via `spring-boot-starter-webmvc` | REST controllers, JSON, embedded Tomcat |
| Spring Data JPA / Hibernate | via `spring-boot-starter-data-jpa` | Entities, repositories, JPA Specifications |
| Bean Validation (Hibernate Validator) | via `spring-boot-starter-validation` | Request validation |
| Spring Security + OAuth2 Resource Server | via `spring-boot-starter-security-oauth2-resource-server` | JWT authentication, role rules, BCrypt |
| Nimbus JOSE + JWT | included by the resource-server starter | Signing and verifying HS256 tokens |
| PostgreSQL | 18 (tested with 18.6) | Database |
| Flyway | via `spring-boot-starter-flyway` + `flyway-database-postgresql` | Database schema migrations |
| Maven Wrapper | `mvnw` / `mvnw.cmd` | Build and tests (no separate Maven install needed) |
| JUnit 5, AssertJ, Mockito, MockMvc | Spring Boot test starters | Tests |

---

## 3. Implemented Features

| Feature | What is implemented |
|---|---|
| Authentication | `POST /api/auth/login` checks the password against a BCrypt hash and returns a signed JWT (HS256, 1 hour by default). Unknown email and wrong password give the same 401. |
| Role-based access | Three roles: `EMPLOYEE`, `MANAGER` and `FINANCE`. The role is read from the database on every request, not trusted from the token. |
| Claim submission and validation | Only `EMPLOYEE` users can submit. Amount, date, category and description are validated (see section 13). New claims start as `SUBMITTED`. |
| Viewing claims | Employees see only their own claims. Managers see claims from their own department. Finance sees all claims, read-only. A claim you are not allowed to see returns 404. |
| Filtering and pagination | `GET /api/claims` supports optional `status`, `category`, `minAmount`, `maxAmount`, `startDate`, `endDate`, `page` and `size` (at most 100). |
| Pending approvals | Managers get a paged list of `SUBMITTED` claims from their own department, oldest first, excluding their own claims. |
| Approval / rejection | Managers can approve or reject `SUBMITTED` claims from their own department, but not their own claims. Rejection needs a comment; approval's comment is optional. |
| Comments | Every decision stores one audit comment (`APPROVE` / `REJECT`, author, text, time), saved in the same transaction as the status change. |
| Concurrency safety | Optimistic locking (`@Version`): if two managers decide the same claim at the same time, only one succeeds and the other gets 409. |
| Monthly report | Totals of `APPROVED` claims per category for the current calendar month. Finance sees the whole organisation; managers see their own department. |
| Error handling | All errors use one JSON format (RFC 9457 "problem details"), without stack traces or SQL. |

**Not implemented** (see section 14): a frontend, user registration or seed users, endpoints to read a
claim's comments or add plain comments, editing or resubmitting claims, logout or refresh tokens, and
choosing a report month.

---

## 4. Architecture Overview

A request goes through these layers:

```
HTTP request
  → Spring Security filter      (verifies JWT, loads employee + role from DB, checks URL role rules)
  → Controller  (web/)          (HTTP ↔ DTOs, @Valid, status codes)
  → Service     (service/)      (business rules, permissions, transactions)
  → Repository  (repository/)   (Spring Data JPA, Specifications, aggregate queries)
  → Entity      (entity/)       (JPA mapping of the Flyway-created tables)
  → PostgreSQL
Errors from any layer → ApiExceptionHandler (web/) → problem-details JSON
```

| Layer | Package | Contents |
|---|---|---|
| Controllers | `web` | `AuthController`, `ExpenseClaimController`, `ClaimApprovalController`, `ReportController`, `ApiExceptionHandler` |
| Services | `service` | `AuthService`, `ExpenseClaimService`, `ClaimApprovalService`, `ExpenseReportService`, and the domain exceptions |
| Repositories | `repository` | `ExpenseClaimRepository` (with `JpaSpecificationExecutor` and two aggregate queries), `ClaimSpecifications`, `EmployeeRepository`, `DepartmentRepository`, `ApprovalCommentRepository` |
| Entities | `entity` | `Department`, `Employee`, `ExpenseClaim`, `ApprovalComment`, and the enums `Role`, `ClaimStatus`, `ExpenseCategory`, `CommentAction` |
| DTOs | `dto` | Java records for every request and response. Controllers never return entities, so password hashes, emails and version numbers are never exposed. |
| Security | `security` | `SecurityConfig`, `JwtTokenService`, `JwtProperties`, `EmployeeJwtAuthenticationConverter`, `CurrentEmployeeProvider`, `SecurityProblemHandler` |
| Config | `config` | `ClockConfig`, a `Clock` bean so tests can fix "today" |
| Migrations | `src/main/resources/db/migration` | `V1__create_initial_tables.sql`. Flyway owns the schema, and Hibernate only validates it (`ddl-auto=validate`). |

Entity relationships: a department has many employees, an employee has many expense claims, and a claim has
many approval comments.

---

## 5. Prerequisites

- **JDK 25.** The project compiles for Java 25, so an older JDK will not work. It was tested with
  Temurin 25.0.4.1. `JAVA_HOME` must point to it.
- **PostgreSQL 18**, running locally on port 5432 (tested with 18.6). Other versions have not been tested.
- **Internet access on the first build**, because the Maven Wrapper downloads Maven and the dependencies.
- Optional: an HTTP client. The examples below use PowerShell's built-in `Invoke-RestMethod`.

Check your Java version:

```powershell
java -version
```

---

## 6. Project Structure

```
expense-claim-processing/
├── pom.xml
├── mvnw, mvnw.cmd, .mvn/wrapper/      Maven Wrapper
├── IMPLEMENTATION_NOTES.md            Design notes, assumptions, open questions
├── README.md
└── src/
    ├── main/
    │   ├── java/com/example/expenseclaims/
    │   │   ├── ExpenseClaimsApplication.java
    │   │   ├── config/        ClockConfig
    │   │   ├── dto/           Request/response records
    │   │   ├── entity/        JPA entities and enums
    │   │   ├── repository/    Repositories, Specifications, CategoryTotal
    │   │   ├── security/      JWT and Spring Security
    │   │   ├── service/       Business logic and exceptions
    │   │   └── web/           REST controllers and ApiExceptionHandler
    │   └── resources/
    │       ├── application.properties
    │       └── db/migration/V1__create_initial_tables.sql
    └── test/
        ├── java/com/example/expenseclaims/   dto, repository, security, service, web tests
        └── resources/application-test.properties
```

---

## 7. Database Setup

The application uses two **dedicated** databases owned by a **non-superuser** login role:

| Database | Used by |
|---|---|
| `expense_claims` | The running application (development) |
| `expense_claims_test` | The automated tests only |

Create them once in `psql`, connected as the `postgres` superuser. Pick your own password and do not reuse
the superuser's:

```sql
CREATE ROLE expense_app WITH LOGIN PASSWORD '<choose-a-strong-password>';
CREATE DATABASE expense_claims      OWNER expense_app;
CREATE DATABASE expense_claims_test OWNER expense_app;
```

`expense_app` is not a superuser. It owns only these two databases, which is all the application needs. Do
not point the application at any other database.

**Flyway migrations:** you do not create the tables yourself. On startup, Flyway runs
`V1__create_initial_tables.sql` (once per database) and records it in `flyway_schema_history`. Hibernate then
checks that the entities match the tables. Never edit a migration that has already been applied; schema
changes go in a new `V2__...sql` file.

### Creating users

There is **no registration endpoint and no seed data**, so users are inserted directly into the database
after the first application start has created the tables. `password_hash` must be a **BCrypt hash**, never a
plain password.

Generate a BCrypt hash with the project's own library. Run this privately, because the password is visible
while you type:

```powershell
.\mvnw.cmd -q dependency:build-classpath "-Dmdep.outputFile=target\classpath.txt"
jshell --class-path (Get-Content target\classpath.txt -Raw).Trim()
```

```java
jshell> new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("<a-demo-password>")
jshell> /exit
```

Then insert a department and one user per role (in `psql` connected to `expense_claims` as `expense_app`).
The names and emails below are examples:

```sql
INSERT INTO departments (name) VALUES ('Sales'), ('Accounts');

INSERT INTO employees (full_name, email, password_hash, role, department_id) VALUES
  ('Demo Employee', 'employee@example.com', '<bcrypt-hash>', 'EMPLOYEE', (SELECT id FROM departments WHERE name = 'Sales')),
  ('Demo Manager',  'manager@example.com',  '<bcrypt-hash>', 'MANAGER',  (SELECT id FROM departments WHERE name = 'Sales')),
  ('Demo Finance',  'finance@example.com',  '<bcrypt-hash>', 'FINANCE',  (SELECT id FROM departments WHERE name = 'Accounts'));
```

Allowed roles are `EMPLOYEE`, `MANAGER` and `FINANCE`. A manager can only approve claims from employees in
**the same department**. Login emails must match exactly (they are case-sensitive).

---

## 8. Environment Variables

Values come from environment variables, so **no secret is stored in the repository**. Never commit
passwords or `JWT_SECRET`. `.gitignore` already excludes `.env`, `.env.*`, `*.pem`, `*.key` and
`application-local.properties`.

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `DB_USERNAME` | yes | none | Database login role (for example `expense_app`) |
| `DB_PASSWORD` | yes | none | Its password |
| `DB_URL` | no | `jdbc:postgresql://localhost:5432/expense_claims` | Application database |
| `TEST_DB_URL` | no | `jdbc:postgresql://localhost:5432/expense_claims_test` | Test database (tests only) |
| `JWT_SECRET` | yes, to **run** the app | none | HMAC key for signing tokens. Must be at least 32 bytes; the app refuses to start without it. |
| `JWT_EXPIRY` | no | `1h` | Token lifetime, for example `30m` or `8h` |

The tests generate their own random JWT secret, so they do not need `JWT_SECRET`.

### Setting them permanently (Windows user variables)

```powershell
[Environment]::SetEnvironmentVariable('DB_USERNAME', 'expense_app', 'User')

# Prompts for the password without showing it on screen
$secure = Read-Host 'Database password' -AsSecureString
$plain  = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure))
[Environment]::SetEnvironmentVariable('DB_PASSWORD', $plain, 'User')
Remove-Variable plain, secure

# Random 48-byte JWT secret; nothing is printed
$bytes = New-Object byte[] 48
[Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
[Environment]::SetEnvironmentVariable('JWT_SECRET', [Convert]::ToBase64String($bytes), 'User')
```

Terminals and IDEs that are already open keep their old environment, so **open a new terminal** afterwards.
Changing `JWT_SECRET` invalidates all existing tokens.

### Loading them into the current terminal only

If a terminal was opened before the variables were set, copy them in without printing them:

```powershell
foreach ($name in 'DB_USERNAME', 'DB_PASSWORD', 'JWT_SECRET') {
    Set-Item "env:$name" ([Environment]::GetEnvironmentVariable($name, 'User'))
}
```

---

## 9. Running the Application

From the project folder:

```powershell
.\mvnw.cmd spring-boot:run
```

The API listens on `http://localhost:8080`, Spring Boot's default port. On the first start Flyway creates
the tables in `expense_claims`.

**If port 8080 is already in use**, choose another port for this terminal only:

```powershell
$env:SERVER_PORT = '8081'
.\mvnw.cmd spring-boot:run
```

Typical startup failures:

| Message | Cause |
|---|---|
| `password authentication failed for user "${DB_USERNAME}"` | `DB_USERNAME` / `DB_PASSWORD` not set in this terminal |
| `Could not bind properties to 'JwtProperties'` | `JWT_SECRET` missing or shorter than 32 bytes |
| `Port 8080 was already in use` | Use `SERVER_PORT` as shown above |

---

## 10. Running Tests

```powershell
.\mvnw.cmd clean test
```

- Tests need **PostgreSQL running** and the `expense_claims_test` database created (section 7).
- They use the `test` profile (`src/test/resources/application-test.properties`), which connects to
  `expense_claims_test` (or `TEST_DB_URL`) using the same `DB_USERNAME` / `DB_PASSWORD`. They never use
  the development database. Do **not** set `TEST_DB_URL` to a database holding real data.
- Most tests roll back their changes. The concurrency tests commit data and then delete it.
- Some `WARN` / `ERROR` log lines (for example `duplicate key` or `violates check constraint`) are
  **expected**: they come from tests that deliberately check the database rejects invalid data. Judge the
  run by the final `Tests run: … Failures: 0, Errors: 0` and `BUILD SUCCESS` lines.

---

## 11. API Reference

Base URL: `http://localhost:8080`. Every endpoint except login needs the header
`Authorization: Bearer <accessToken>`. A missing or invalid token gives **401**; a valid token with the
wrong role gives **403**.

| Method | Path | Roles | Request | Success response |
|---|---|---|---|---|
| POST | `/api/auth/login` | public | `{"email", "password"}` | 200 `{accessToken, tokenType: "Bearer", expiresIn, expiresAt, employee, role}` (sent with `Cache-Control: no-store`) |
| POST | `/api/claims` | EMPLOYEE | `{"category", "amount", "expenseDate", "description"?}` | 201 claim + `Location: /api/claims/{id}` |
| GET | `/api/claims` | any logged-in user | Optional query parameters: `status`, `category`, `minAmount`, `maxAmount`, `startDate`, `endDate`, `page` (default 0), `size` (default 20, max 100) | 200 page of claims you may see, newest first |
| GET | `/api/claims/mine` | any logged-in user | none | 200 list of your own claims, newest first |
| GET | `/api/claims/{claimId}` | any logged-in user | none | 200 claim, or 404 if it doesn't exist or you may not see it |
| GET | `/api/claims/pending-approvals` | MANAGER | `page`, `size` | 200 page of your department's `SUBMITTED` claims (not your own), oldest first |
| POST | `/api/claims/{claimId}/approve` | MANAGER | optional `{"comment"}` (≤ 1000 chars) | 200 `{claim, decision}` |
| POST | `/api/claims/{claimId}/reject` | MANAGER | required `{"comment"}` (not blank, ≤ 1000 chars) | 200 `{claim, decision}` |
| GET | `/api/reports/monthly-expenses-by-category` | MANAGER, FINANCE | none | 200 monthly report |

Which claims each role can see (`GET /api/claims`, `/{claimId}`):

| Role | Sees |
|---|---|
| EMPLOYEE | Own claims only |
| MANAGER | All claims of employees in the manager's department, including the manager's own |
| FINANCE | All claims (finance has no write endpoints) |

Notes:

- Values: `status` is `SUBMITTED`, `APPROVED` or `REJECTED`. `category` is `TRAVEL`, `MEALS`,
  `ACCOMMODATION`, `OFFICE_SUPPLIES`, `TRAINING` or `OTHER` (uppercase, case-sensitive). Dates use
  `yyyy-MM-dd`. Amount and date ranges include both ends.
- Parameters such as `employeeId` or `departmentId` are ignored. What you can see is decided only by your
  role and department.
- A claim response contains `id, category, amount, expenseDate, description, status,
  submittedBy {id, fullName, departmentName}, createdAt, updatedAt`.
- A page response contains `content, page, size, totalElements, totalPages`.
- `decision` contains `id, action (APPROVE/REJECT), commentText, author {id, fullName, departmentName},
  createdAt`.
- The report contains `month, periodStart, periodEndExclusive, scope ("ORGANIZATION" for finance,
  "DEPARTMENT" for managers), departmentName (the manager's department; empty for finance), totals
  [{category, total}], grandTotal`.

---

## 12. Example Workflow (PowerShell)

This assumes the app is running and the demo users from section 7 exist. Replace the placeholder passwords
with the ones you chose, and never paste real tokens into documents or commits.

> **Note:** these commands follow the controllers and the MockMvc tests, but this exact live sequence has
> not yet been run against the development database (see section 15).

```powershell
$base = 'http://localhost:8080'

function Get-AuthHeader($email, $password) {
    $body = @{ email = $email; password = $password } | ConvertTo-Json
    $login = Invoke-RestMethod -Method Post -Uri "$base/api/auth/login" -ContentType 'application/json' -Body $body
    @{ Authorization = "Bearer $($login.accessToken)" }
}

# 1. Employee logs in and submits a claim dated today
$employee = Get-AuthHeader 'employee@example.com' '<employee-password>'
$claimBody = @{
    category    = 'TRAVEL'
    amount      = 125.50
    expenseDate = (Get-Date -Format 'yyyy-MM-dd')
    description = 'Taxi to client meeting'
} | ConvertTo-Json
$claim = Invoke-RestMethod -Method Post -Uri "$base/api/claims" -Headers $employee -ContentType 'application/json' -Body $claimBody
$claim.status          # SUBMITTED

# 2. Employee lists their own claims, with a filter
Invoke-RestMethod -Uri "$base/api/claims?status=SUBMITTED&category=TRAVEL" -Headers $employee

# 3. Manager (same department) logs in and opens the pending-approval queue
$manager = Get-AuthHeader 'manager@example.com' '<manager-password>'
(Invoke-RestMethod -Uri "$base/api/claims/pending-approvals" -Headers $manager).content

# 4a. Manager approves the claim (comment optional) ...
$approveBody = @{ comment = 'Approved' } | ConvertTo-Json
$decision = Invoke-RestMethod -Method Post -Uri "$base/api/claims/$($claim.id)/approve" -Headers $manager -ContentType 'application/json' -Body $approveBody
$decision.claim.status       # APPROVED

# 4b. ... or rejects it instead (comment required):
# $rejectBody = @{ comment = 'Receipt missing' } | ConvertTo-Json
# Invoke-RestMethod -Method Post -Uri "$base/api/claims/$($claim.id)/reject" -Headers $manager -ContentType 'application/json' -Body $rejectBody

# 5. Monthly report: manager (own department) and finance (whole organisation)
Invoke-RestMethod -Uri "$base/api/reports/monthly-expenses-by-category" -Headers $manager
$finance = Get-AuthHeader 'finance@example.com' '<finance-password>'
Invoke-RestMethod -Uri "$base/api/reports/monthly-expenses-by-category" -Headers $finance
```

`Invoke-RestMethod` throws an error for 4xx/5xx responses. Approving the same claim a second time, for
example, shows a 409 error.

---

## 13. Validation and Error Handling

### Claim submission rules (`CreateExpenseClaimRequest`)

| Field | Rule | Matches the database |
|---|---|---|
| `category` | required, one of the six categories | `NOT NULL` + `CHECK` on category |
| `amount` | required, greater than 0, at most 10 digits before and 2 after the decimal point | `NUMERIC(12,2)`, `CHECK (amount > 0)` |
| `expenseDate` | required, valid ISO date, not in the future (server's time zone) | `DATE NOT NULL` (the no-future-date rule exists only in Java) |
| `description` | optional, at most 500 characters | `VARCHAR(500)` |

Other request rules: login `email` is required (≤ 255 characters) and `password` is required (≤ 72
characters, BCrypt's limit). Approval and rejection comments are at most 1000 characters, and a rejection
comment must not be blank. In searches, `minAmount` must not exceed `maxAmount`, and `startDate` must not
be after `endDate`.

### Status codes

All errors return `application/problem+json`, for example:

```json
{ "type": "about:blank", "title": "Invalid request", "status": 400,
  "detail": "The request body contains invalid fields.",
  "errors": { "amount": "amount must be greater than zero" } }
```

| Status | When |
|---|---|
| 400 | Invalid body fields (with an `errors` map), malformed JSON, unknown enum value, bad date or number, invalid paging, contradictory filters |
| 401 | Wrong email or password (`"Invalid email or password."`), or a missing, expired or tampered token (sent with `WWW-Authenticate: Bearer`) |
| 403 | Wrong role (for example a manager submitting a claim, or an employee opening the report), or a manager deciding their own claim |
| 404 | Unknown claim, or a claim you are not allowed to see (including another department's claim for a manager) |
| 409 | Deciding a claim that is not `SUBMITTED` (already approved or rejected), or losing a concurrent decision (optimistic locking) |
| 500 | Unexpected server error. Only a generic message is returned; details are logged on the server. |

---

## 14. Assumptions and Limitations

Confirmed by the mentor:

- **Single-stage approval:** the manager's decision is final, and there is no finance approval stage (C1).
- Monthly totals include **`APPROVED`** claims only (C2).
- Managers do not submit claims (C3).

Working assumptions not yet confirmed (details and IDs in `IMPLEMENTATION_NOTES.md`):

- Finance is **read-only**: it can see all claims and the organisation-wide report, with no write actions (A3).
- Only `EMPLOYEE` users submit claims; finance users do not (A9).
- A manager may decide any claim from their department except their own. There is no direct-report hierarchy
  or assigned approver (A2, A11, A12).
- "Today" and the "current month" use the **server's default time zone** (A4, A6).
- Categories are a fixed list. Amounts are in a single currency with no upper limit (A7, A8).
- `APPROVED` and `REJECTED` are final; there is no editing or resubmission (A5).

Known limitations:

- No frontend, no registration endpoint and no seed data. Users are created with SQL (section 7).
- **Comments:** only the single approve/reject decision comment is created through the API. The database
  model allows many comments per claim (including plain `COMMENT` entries), but there is **no endpoint to add
  plain comments or to read a claim's comments**. An employee cannot see a rejection reason through the API.
- The report covers the current month only; there is no `?month=` parameter.
- List sorting is fixed: newest first, and pending approvals oldest first.
- No logout, token revocation, refresh tokens, login rate limiting or account lockout. Login emails are
  case-sensitive.
- The open questions for the mentor are listed at the end of `IMPLEMENTATION_NOTES.md`.

---

## 15. Current Verification Status

| Check | Result |
|---|---|
| Automated tests, re-run while writing this README (2026-10-18, commit `4c26522`, JDK 25.0.4.1, PostgreSQL 18.6, `expense_claims_test`) | `.\mvnw.cmd clean test`: **146 tests, 0 failures, 0 errors, 0 skipped. BUILD SUCCESS** |
| What the tests cover | Validation, repositories, JWT verification (expired, tampered and wrong-issuer tokens), role rules, visibility per role, filters, paging, pending queue, approval/rejection rules, 409 on double decisions, an 8-thread concurrent-approval test, transaction rollback, report totals and month boundaries, a statement-count check against N+1 queries |
| Running the application against `expense_claims` | **Not yet verified.** The app has not been started against the development database, and the workflow in section 12 has not been run live. `JWT_SECRET` must be set and demo users created first. |
| Open verification items | The lost-race 409 is tested at service level and in the exception handler, but not end-to-end over HTTP. It is unconfirmed whether the organisation-wide report writes `"departmentName": null` or omits the field; the test accepts either. |

---

## 16. Future Improvements (not implemented)

These are possible next steps, **not** existing features:

- `GET /api/claims/{id}/comments`, so employees can read rejection reasons, and possibly
  `POST /api/claims/{id}/comments` for plain comments (depends on open question 4).
- A documented, local-only demo data script.
- A `?month=yyyy-MM` parameter for the report, and a configurable company time zone.
- Client-selectable sorting for claim lists.
- Case-insensitive login emails, login rate limiting, and refresh tokens or logout.
- An end-to-end HTTP test for concurrent approvals.
- OpenAPI/Swagger documentation and a simple frontend.
