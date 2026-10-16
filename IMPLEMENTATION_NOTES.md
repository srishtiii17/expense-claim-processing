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
| C3 | **Managers do not need to submit their own claims.** | A2 (submission part); resolves open question 3 | No code yet. Claim submission (Checkpoint 2/3) should be limited to the `EMPLOYEE` role, so a manager never approves their own claim. |

## Business-rule assumptions (not yet confirmed by mentor)

| # | Assumption | Where it shows up in code |
|---|---|---|
| ~~A1~~ | Withdrawn. Replaced by C1 (single-stage approval); no code still uses it. | — |
| A2 | Managers view their department's claims and approve or reject them (submission part superseded by C3) | Checkpoints 3–4 |
| A3 | Finance can view claims across all departments (read-only, because of C1; exact finance powers still open) | Checkpoints 3–5 |
| A4 | Expense date cannot be in the future; no maximum age | Checkpoint 2 |
| A5 | Rejection requires a comment; rejected claims are final; no resubmission in v1 | Checkpoint 4 |
| A6 | Monthly totals are grouped by `expense_date` for the current calendar month (the `APPROVED`-only part is confirmed by C2) | Checkpoint 5 |
| A7 | Categories are a fixed list: TRAVEL, MEALS, ACCOMMODATION, OFFICE_SUPPLIES, TRAINING, OTHER | `ExpenseCategory` enum + DB CHECK |
| A8 | Amount is stored as `NUMERIC(12,2)`; single currency; no upper limit | `ExpenseClaim.amount`, V1 migration |

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
(no owner, id or status fields). No validation annotations yet (Checkpoint 2).

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
