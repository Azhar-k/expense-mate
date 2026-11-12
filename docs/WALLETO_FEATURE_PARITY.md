# Walleto vs Expense Mate: feature parity and production gaps

Captured 2026-11-08 from a read of the Walleto repos, so they don't need scanning again when designing the
missing pieces. Status column updated 2026-11-12.

Sources read:

- `E:\Development\IdeaProjects\walleto-core`: Spring Boot backend (Java 17, Postgres, Flyway)
- `E:\Development\IdeaProjects\usermanagement`: Spring Boot auth and user service
- `E:\Development\IdeaProjects\walleto-deploy`: docker-compose, nginx gateway, setup script
- `walleto-ui`: **not on disk**, only the published image `ghcr.io/azhar-k/walleto-ui`. Anything that exists
  only in the UI is not covered here.

---

## 1. Gap summary

| # | Walleto feature | Expense Mate | Status |
|---|---|---|---|
| 1 | Multiple currencies per account | Was ₹ only | **Done** on branch `multi-currency` (see §5) |
| 2 | Message patterns stored per user, editable (`/api/regex`) | About 20 Indian patterns hardcoded in `util/SmsTransactionHandler.java` | Missing |
| 3 | Receipt attachments on a transaction | None | Missing |
| 4 | Audit log of every create, update and delete, searchable | None | Missing |
| 5 | Accounts, sign-in, roles (usermanagement) | Google sign-in for Drive backup only | Missing |
| 6 | Per-user data (a database per tenant) | Single user, single device | Missing |
| 7 | Sync across devices and web | Phone only; Drive backup and restore | Missing |
| 8 | Message processing over HTTP, single and bulk, with duplicate counts | Captured on device only | Missing |
| 9 | Amount range filter (min and max) | Exact amount only | Missing |
| 10 | Mark all bills done or not done at once | One bill at a time | Missing (check the UI) |
| 11 | OpenAPI docs and a health endpoint | Not applicable without a backend | n/a |

Expense Mate already goes beyond Walleto in: real and virtual accounts, money lent, reordering, suggestions from
past data, RCS capture, the needs-review queue, and default category and account per type. Nothing there needs
porting back.

---

## 2. walleto-core reference

### 2.1 Endpoints

All take a JWT. The tenant id comes from the token (`SecurityContextUtil.getCurrentUserId()`).

| Area | Method and path | Notes |
|---|---|---|
| Accounts | `POST/GET /api/accounts`, `GET/PUT/DELETE /api/accounts/{id}` | |
| | `PUT /api/accounts/{id}/default`, `GET /api/accounts/default` | One default account |
| Categories | `POST/GET /api/categories`, `GET/PUT/DELETE /api/categories/{id}` | |
| Transactions | `POST /api/transactions`, `GET/PUT/DELETE /api/transactions/{id}` | |
| | `POST /api/transactions/search` | Body: `TransactionSearchRequest` (§2.3) |
| | `PATCH /api/transactions/{id}/exclude-from-summary` | |
| | `PATCH /api/transactions/{id}/link-recurring-payment/{recurringPaymentId}` | |
| Attachments | `POST /api/transactions/{transactionId}/attachments` (multipart) | 5 MB max; `image/jpeg`, `image/png`, `application/pdf` only |
| | `GET .../attachments`, `GET .../attachments/{attachmentId}/download`, `DELETE .../attachments/{attachmentId}` | Stored as `BYTEA` in Postgres |
| Transfers | `POST /api/transfers` | Body: `fromAccountId, toAccountId, amount, dateTime, categoryId, description` |
| Balance | `GET /api/balance`, `GET /api/balance/{accountId}` | Net, or one account |
| Summary | `GET /api/summary/{accountId}?startDate&endDate` | `totalExpense, totalIncome, totalBalance`; ISO date-times, both optional |
| | `GET /api/summary/{accountId}/categories/expense`, `/categories/income` | Totals per category |
| Recurring | `POST/GET /api/recurring-payments`, `GET/PUT/DELETE /api/recurring-payments/{id}` | Fields: `name, amount, dueDay, expiryDate, isCompleted, lastCompletedDate` |
| | `PUT /api/recurring-payments/{id}/complete`, `PUT /api/recurring-payments/toggle-all` | `toggleAll(boolean completed)` |
| Regex | `GET/POST /api/regex`, `GET/PUT/DELETE /api/regex/{id}` | Per user; `name, pattern, transactionType, isActive` |
| Messages | `POST /api/sms/process` (`{body, sender, timestamp}`), `POST /api/sms/process/bulk` (`{messages: [...]}`) | Bulk returns `createdTransactions, duplicatesIdentified, patternNotMatched, processingError` |
| Audit | `GET /api/audit-logs`, `POST /api/audit-logs/search` | Search: `fromDate, toDate, action, entityType, entityId, username, query` |
| Onboarding | `POST /api/onboarding` | Seeds a new user (§2.5) |
| Health | `GET /health` | |
| Docs | `/api-docs`, `/swagger-ui.html` | springdoc |

Roles are checked with `@RequireRole` and `RoleBasedAccessInterceptor`. JWTs are validated locally
(`JwtTokenValidator`) with the secret shared with usermanagement.

### 2.2 Data model (Flyway V1–V8)

Every table has `tenant_id`, `created_at`, `updated_at` and `version` (optimistic locking). Ids are `BIGINT` identity
columns. Amounts are `DECIMAL(19,2)`.

- **accounts**: `name` (unique per tenant), `account_number`, `bank`, `currency` (enum, 20 codes), `expiry_date`,
  `description`, `is_default`
- **categories**: `name` (unique per tenant), `type` (`EXPENSE` or `INCOME`)
- **recurring_payments**: `name, amount, due_day, expiry_date, is_completed, last_completed_date`. There are no
  from and to accounts, unlike Expense Mate bills.
- **transactions**: `amount, transaction_type (DEBIT/CREDIT), date_time, description, category_id (FK),
  account_id (FK), counterparty_name, sms_body, sms_sender, sms_hash, recurring_payment_id (FK),
  is_exclude_from_summary`. Unique on `(tenant_id, sms_hash)`. One account per row: there are no real and
  virtual accounts, and no transfer group.
- **regex_patterns**: `name` (unique per tenant), `pattern`, `transaction_type`, `is_active`
- **audit_logs**: `entity_type, entity_id, action, username, timestamp, changes (text), description, request_path,
  request_method`
- **transaction_attachments**: `transaction_id` (FK, cascade delete), `file_name, file_type, file_size, file_data
  (BYTEA)`. V8 replaced a file-system `storage_location` with the bytes in the database.

Currency enum: USD, EUR, GBP, INR, AED, SAR, CAD, AUD, JPY, CNY, CHF, SGD, HKD, NZD, SEK, NOK, DKK, ZAR, MXN, BRL.

### 2.3 Transaction search filters

`fromDate, toDate, transactionType, query` (free text over description and counterparty), `description,
counterpartyName, categoryId, accountId, recurringPaymentId, minAmount, maxAmount, isExcludeFromSummary`.

### 2.4 How message processing works

1. `RegexPatternService.getMatchingPattern(body)` picks the first active user pattern that matches.
2. The matcher's groups are classified by shape, not by position:
   - a masked number such as `XX678`, `Acct 678` or `*1234` becomes the account suffix (8 digits at most);
   - a number becomes the amount;
   - a date becomes the date;
   - the rest becomes the counterparty.
3. The transaction is created through `TransactionService.create`, and the `sms_hash` unique key rejects
   duplicates.
4. Bulk processing counts each outcome: created, duplicate, no pattern matched, error.

Patterns are configuration, not code. That lets a user outside India add their own bank, which is the main reason
gap #2 matters.

### 2.5 Onboarding seeds (`OnboardingService`)

Each new tenant gets an account "HDFC Bank" (INR, a fake number, default), categories "Utilities" (expense) and
"Salary" (income), and the Indian regex set (ICICI, Kotak, SBI, Federal, Pluxee, general UPI, NEFT and card
spends; the full list is commented in `V6__create_audit_logs_table.sql`). This needs to depend on the user's region
before Walleto can go worldwide.

### 2.6 Multi-tenancy

`TenantDatabaseManager` creates a Postgres database `walleto_tenant_<tenantId>` per user and runs Flyway on it. The
startup Flyway run is turned off. `TenantDataSource` routes each request by `TenantContext`. Log lines carry
`tenantId`, `userId` and `requestId` via MDC.

---

## 3. usermanagement reference (`com.klm.user.management`)

| Controller | Paths |
|---|---|
| Auth `/api/v1/auth` | `POST /login`, `/register`, `/refresh`, `/logout`, `/change-password` |
| Users `/api/v1/users` | `GET/PUT /me`, `GET /{id}`, `GET /` |
| Admin `/api/v1/admin` | `POST /users`, `PUT/DELETE /users/{id}`, `PUT /users/{id}/status`, `POST /users/{id}/reset-password`, `POST/DELETE /users/{id}/roles`, `GET /audit-logs` |
| Roles `/api/v1/roles` | `POST`, `GET`, `GET/PUT/DELETE /{id}`, `GET /name/{name}` |
| Token `/api/v1/token` | `POST /validate`, `GET /info` |

User: `username, email, phone, password, status (ACTIVE, INACTIVE, LOCKED, DELETED), roles, createdAt, updatedAt`.
JWTs are signed with `JWT_SECRET`; `JWT_EXPIRATION` and `JWT_REFRESH_EXPIRATION` are set in the deploy (60
each). It has its own audit trail (ROLE_CREATED, ROLE_ASSIGNED and so on).

---

## 4. walleto-deploy reference

- docker-compose runs `walleto-core` (port 8080), `usermanagement` (8073), an nginx gateway (8071), Postgres 15
  (5432) and `walleto-ui` (80).
- The gateway routes `/api/core/` to core and `/api/userservice/` to usermanagement.
- Images are built and pushed to GHCR by GitHub Actions (`mvn clean verify`, then a Docker build). Compose pins
  each image by `sha-...` tag.
- Not production-safe as it stands:
  - real-looking default `JWT_SECRET` and Postgres password inside `docker-compose.yml`;
  - Postgres port 5432 published to the internet;
  - no TLS, only plain HTTP on 80 and 8071;
  - no database backups;
  - attachments kept as BYTEA in the database rather than in object storage;
  - `logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE` in the base `application.properties`,
    which logs bound SQL values, meaning transaction data;
  - JVM heap capped at 256 MB per service.

---

## 5. Multi-currency in Expense Mate (gap #1, done)

Branch `multi-currency`, database version 15. It is designed differently from Walleto on purpose:

- Real accounts hold one ISO 4217 currency (any code, not a fixed enum). Virtual accounts hold none and show one
  balance per currency they contain.
- A transaction's currency always follows its real account. `TransactionRules.applyCurrency` enforces this on
  every write path.
- Amounts in different currencies are never added together. Every SQL total groups by currency, and Home has a
  currency switcher when a month has more than one.
- The main currency is the default real account's currency; there is no separate setting.
- Transfers between currencies store both amounts. Loans and bills have their own currency.
- The upgrade labels all existing data INR. Backups carry the currency, and older backups restore as INR.
- Not done: foreign spends (the original amount and currency kept alongside the charged amount), reading the
  currency from message text, exchange rates and a converted total, and locale-specific separators.

---

## 6. Production gaps for a worldwide release (either app)

### Blockers

1. **Package name `com.example.expensemate`.** Google Play rejects `com.example.*`, and it can't change after the
   first release. Deferred to the end on purpose.
2. **Play SMS permission policy.** `READ_SMS` and `RECEIVE_SMS` need the Permissions Declaration Form ("SMS-based
   money management" exception), a privacy policy URL, and an in-app disclosure before the permission prompt.
   None of these exist yet.
3. **Currency.** Done (§5).
4. **Message parsing is India-only.** Needs user-defined patterns (gap #2), or per-country rule sets that can be
   downloaded.
5. **No translations.** `values/strings.xml` has a single entry. About 20 layouts and about 170 places in the Java
   code set text directly; lint counts 136 HardcodedText warnings. RTL is declared but untested.

### Strongly recommended

6. Release build hardening: `minifyEnabled false`, so turn on R8 with keep rules for Room and the Google APIs.
7. Crash reporting: none at the moment.
8. App lock with biometrics or a PIN.
9. User data rights: delete all data in the app, export (CSV or PDF), Play's Data Safety form, and account deletion
   if a backend is added.
10. Bill reminders: no notifications for due dates (Walleto has none either).
11. Dates: check fixed patterns such as `"d MMM"` and `"MM/yy"`, month boundaries across time zones, and
    `dueDay` 31 in short months.
12. Locale number input: `Money.parse` strips `,`, so `12,50` in a comma-decimal locale reads as 1250.

### If Expense Mate moves to the Walleto backend

13. Fix the deploy issues in §4.
14. Make onboarding seeds depend on the user's region (§2.5).
15. Sending raw bank messages to a server raises the privacy bar: data residency, encryption at rest, and a
    consent screen.
16. The models differ: Walleto has one account per transaction, no real and virtual split, no transfer groups,
    loans, sort order or needs-review state, and categories linked by id rather than by name. A sync design must
    choose which side's model wins.
