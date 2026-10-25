# Appointment Platform — Backend

Java 21, Spring Boot 4.1.1, Spring Modulith 2.1.1, PostgreSQL, JPA, Flyway and server-side Spring Security sessions.

## Domain and modules

- `identity`: accounts, email verification, login, password reset, admin account status management.
- `brand`: brand identity and registration orchestration.
- `branch`: branch onboarding, address/maps data, weekly working hours and appointment-acceptance preference.
- `notification`: transactional encrypted mail queue and worker.
- `audit`: security and management events scoped by `brandId`.
- `shared`: crypto, rate limits and API errors.

Dependencies: Identity → Brand; Identity → Notification; Identity and Branch → Audit. Modules access only their own repositories. Public module contracts are at package roots. `ApplicationModules.verify()` checks the declared boundaries.

One BRAND account owns one Brand through a unique `users.brand_id`. ADMIN accounts have no brand. A Brand can have multiple Branch records. Registration creates no branch; owners create their branches after logging in.

`POST /api/auth/registrations` accepts account fields and `displayName`, without address/maps fields. Email verification creates the account and brand in one transaction.

`POST /api/panel/branches` accepts `name`, `address`, optional HTTPS `googleMapsUrl`, and a non-empty `workingHours` list of `{dayOfWeek, startTime, endTime}`. Days use Java enum names (e.g. `MONDAY`); times use local Turkey wall-clock time (e.g. `08:00`). Branch and hours are saved atomically. A day with no interval is closed. Multiple non-overlapping intervals per day are supported; overnight intervals are not supported.

`GET /api/panel/branches/{id}/working-hours` reads the weekly schedule. `PUT` at the same URL replaces the whole schedule with `{ "workingHours": [...] }`; an empty list closes every day. Updates lock the branch to serialize schedule replacements. These endpoints require the owning brand. Existing branches are preserved without inventing default hours; owners must configure their schedules. Branch date-specific exceptions are supported; appointment start-time preview and initial creation APIs are implemented, with limitations below.

## Account and branch state

| Field | Default at registration | Manager | Meaning |
|---|---|---|---|
| User.enabled | true | Admin | Login permission; disabling revokes existing sessions even if later re-enabled |
| User.subscriptionActive | false | Admin, based on payment | Subscription entitlement, independent of panel login |
| Branch.acceptingAppointments | false | Brand owner | Preference to accept new appointments |

Future booking screens and new appointment creation require `subscriptionActive && acceptingAppointments`. Initial appointment creation APIs exist, and creation enforces this entitlement check. Deactivating a subscription does not block panel access or existing-booking management. Payment-provider integration is future work.

## APIs

Authentication, CSRF, email verification, password reset and logout routes are unchanged. Login and `/api/auth/me` return `brandId`, `enabled` and `subscriptionActive` alongside identity details.

| Endpoint | Role | Body |
|---|---|---|
| GET /api/panel/brand | BRAND | — |
| GET /api/panel/branches | BRAND | — |
| POST /api/panel/branches | BRAND | `name`, `address`, `googleMapsUrl`, `workingHours` |
| GET /api/panel/branches/{id}/working-hours | Owning BRAND | — |
| PUT /api/panel/branches/{id}/working-hours | Owning BRAND | `workingHours` (full replacement) |
| PATCH /api/panel/branches/{id}/appointment-acceptance | Owning BRAND | `{"acceptingAppointments":true}` |
| GET /api/admin/users?page=0&size=20 | ADMIN | — |
| PATCH /api/admin/users/{id}/status | ADMIN | `{"enabled":true,"subscriptionActive":true}` |

Mutations require CSRF. Admin listings/status updates target BRAND accounts only. Admin cannot modify branch preferences. Brand owners cannot grant themselves subscription or account status. Previous tenant routes are removed; clients must update.

## Migrations V5–V6

Existing brand/account IDs and addresses/maps data are preserved; one “Merkez” branch is created per former tenant. V5 temporarily retains old approval/address/maps columns with a `legacy_` prefix. V6 removes these three unused columns; current addresses/maps remain on Branch. Old platform approval is not interpreted as payment. New subscription flags and branch preferences default to false; review actual paid entitlements before rollout. An already present `subscription_active` column is preserved. Old Flyway migrations are unchanged. No production/development database is migrated by the coding task; Flyway applies pending migrations when this application is started.

## Run locally

You need Java 21 and Docker. A separate Maven installation is not required.

1. Generate a 32-byte encryption key:

   ```sh
   openssl rand -base64 32
   ```

2. Create a local `.env.dev` file in this directory. Replace the password and key placeholders with your own values; use the output from the previous command for the key:

   ```dotenv
   DB_PASSWORD=replace-with-a-local-development-password
   APP_SECURITY_ENCRYPTION_KEY=replace-with-your-base64-key
   MAIL_HOST=localhost
   MAIL_PORT=1025
   MAIL_FROM=no-reply@localhost
   ADMIN_BOOTSTRAP_ENABLED=false
   ```

3. Load the local variables, start PostgreSQL and Mailpit, then run the `dev` profile:

   ```sh
   set -a
   . ./.env.dev
   set +a
   docker compose up -d --wait
   SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
   ```

The `dev` profile also imports `.env.dev` from the working directory. Exporting it in the shell makes `DB_PASSWORD` available to Docker Compose as well. Do not commit `.env.dev` or any real credentials.

PostgreSQL listens on `localhost:5433`, Mailpit's web UI is at [localhost:8025](http://localhost:8025), and the API uses port `8080`. Check [the health endpoint](http://localhost:8080/actuator/health) after startup. `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `APP_SECURITY_ENCRYPTION_KEY`, and mail settings can be supplied through environment variables. The Compose file is for local development, not production deployment.

### First admin account

To create the initial admin, set `ADMIN_BOOTSTRAP_ENABLED=true` and provide `ADMIN_BOOTSTRAP_EMAIL`, `ADMIN_BOOTSTRAP_PASSWORD`, and `ADMIN_BOOTSTRAP_TOTP_SECRET` in `.env.dev` before starting the application. The password must be 12–128 characters. Use an uppercase, unpadded Base32 TOTP secret generated from at least 20 random bytes. Once the account exists, set `ADMIN_BOOTSTRAP_ENABLED=false` and remove the bootstrap password and TOTP secret from the runtime environment. Admin login requires both the password and a six-digit TOTP code.

## Tests

Run `./mvnw verify` with Docker running. Testcontainers uses isolated PostgreSQL databases and does not send real email. Tests cover atomic registration/verification, concurrent verification, brand/branch isolation, admin-only account controls, owner-only branch preferences, subscription-independent panel access, disabled-account session revocation, password reset, MFA replay, CSRF and module boundaries.

## Future work

Branch creation UI, booking lifecycle, public booking screens, SMS and payment-provider integration. Branch creation/weekly-hours APIs, service creation/listing and employee management/assignments are implemented.

## Employee working hours

All endpoints below are scoped to the employee's owning brand under `/api/panel/employee/{employeeId}`:

| Method | Path | Behavior |
| --- | --- | --- |
| GET | `/working-hours` | Weekly configuration and `usesBranchHours` flag |
| PUT | `/working-hours` | Replace weekday customizations with `workingHours` entries and optional `closedDays` enum array |
| DELETE | `/working-hours` | Restore branch inheritance, preserving date overrides |
| GET | `/schedule/{date}` | Resolve one date, returning source, active flag, configured and effective hours |
| PUT | `/schedule/{date}` | Replace date override with `workingHours` entries (`startTime`, `endTime`) |
| DELETE | `/schedule/{date}` | Remove date override and fall back to weekly configuration |

No custom weekly record means branch inheritance. Weekly overrides are per weekday: explicit `closedDays` are closed, days with configured intervals use those intervals, and all other weekdays inherit the branch. An empty employee `workingHours` list with empty/omitted `closedDays` restores inheritance for every day. PUT replaces the whole override set; it is not a patch. An empty date override means that date is closed. Date overrides take precedence over employee weekday settings, including explicit weekday closures. With no date override, an unconfigured weekday uses effective branch date hours (including branch overrides), not merely branch weekly hours. Only today/future date overrides may be changed, using Europe/Istanbul to determine today. Intervals must be non-overlapping, within one day and inside branch working hours. Adjacent branch intervals count as continuous opening.

All employee schedule mutations lock the employee row to serialize concurrent updates. Date overrides are unique by employee/date. Effective daily hours always intersect with current branch hours, so later branch changes cannot expose closed time; inactive employees return no effective hours. The weekly endpoint returns combined `workingHours`, employee-only `configuredHours`, `closedDays` and `inheritedDays`. `usesBranchHours` means all seven days inherit; false can mean partial inheritance. Use configuredHours/closedDays when editing, not the combined intervals. These are working hours, not booking availability. V13 preserves legacy empty schedules as seven explicit closedDays; omitted weekdays of non-empty legacy schedules now inherit branch hours. Branches without configured hours are closed.

These are schedule APIs, not booking APIs. Available start-time preview exists; existing PENDING/APPROVED appointments are subtracted and overlapping creation is rejected. No appointments are cancelled by schedule changes.

## Branch date overrides

`GET /api/panel/branches/{branchId}/schedule/{date}` returns `{date, dateOverride, workingHours}`. `PUT` replaces that date with `{workingHours: [{startTime, endTime}]}`; an empty list explicitly closes it. `DELETE` removes the override and restores the owner-defined weekly schedule. Dates use `YYYY-MM-DD`; edits are limited to today/future dates in Europe/Istanbul. Multiple intervals are allowed without overlap, and a date override can open a normally closed weekday. Writes lock the branch row and persist the header/intervals atomically; employee/date and branch/date overrides are independent.

Employee daily resolution and date validation now use effective branch date hours. Employees inheriting branch hours follow the override directly; employee custom weekly/date hours are intersected with it. Closing a branch yields no employee hours, even with employee date overrides. Restoring branch weekly hours preserves employee customizations. Appointment creation now rejects conflicts; schedule changes still do not cancel existing bookings.

## Available slot preview

`GET /api/panel/appointment/available-slot/{serviceId}/{employeeId}/{date}` requires BRAND authentication, service ownership and an employee-service assignment. It returns `{ "availableAppointmentSlotDetails": { "serviceId": "...", "employeeId": "...", "localTimes": [...] } }`.

Candidate starts advance by the selected service duration from each effective employee working interval. The entire service must fit before the interval end. Starts must be at least one hour after the injected clock in Europe/Istanbul. Past dates and closed/inactive schedules return an empty list. This preview subtracts PENDING/APPROVED bookings; no reservation is made. Creation rechecks booking/subscription rules and database constraints.

Tests include fixed-clock lead-time boundaries, midnight rollover, split working intervals, different service durations, past/closed schedules, HTTP ownership/assignment checks and branch date closures.

## Appointment creation — 12 October 2026

- `POST /api/panel/appointment/create`: owning BRAND session; past and near-future appointments are allowed if they fit working hours.
- `POST /api/public/appointment/create/{branchId}`: no login required; CSRF token and matching cookie are still required. The employee must belong to this exact branch. Customer starts must be at least one hour ahead.
- Both accept `employeeId`, `serviceId`, `date` (YYYY-MM-DD), `startTime` (HH:mm), customer first/last name and phone; email is optional. `source` is determined by the endpoint.
- Both return 201 with id, branchId, employeeId, serviceId, serviceName, durationMinutes, startsAt, endsAt and status. Source is stored, but is not included in the response.
- Istanbul local date/time is converted to `Instant`; PostgreSQL stores `timestamptz`. Status is initially PENDING; service name and duration are snapshots.
- V14 creates appointments, V15 adds source, V16 aligns types and makes source mandatory. V16 stops if legacy time-only rows exist: their appointment dates must be recovered, not guessed. Applied migrations must not be edited.
- Appointment accesses employee/branch/service through module-root interfaces. Shared schedule reads and relationship checks do not require a BRAND session; panel entry methods retain authorization.
- Remaining work: lifecycle actions, notifications. Passing current tests does not mean these features exist.

### Appointment overlap protection — 16 October 2026

Creation checks existing PENDING/APPROVED bookings and returns 409 for overlaps. V17 adds PostgreSQL `btree_gist` and the `ex_appointments_employee_time` exclusion constraint as the concurrency guarantee. It applies to the same employee across all services, using [start, end) intervals; adjacent bookings remain valid. A database collision also returns 409 with an appointment-specific message. Existing conflicting data causes migration failure and is never deleted automatically. Migration is applied on backend restart; the database migration account needs permission to install btree_gist.

### New-booking permission — 16 October 2026

Both BRAND and CUSTOMER creation require the brand account's active subscription and the selected branch's `acceptingAppointments=true`. The shared create path enforces both checks through `SubscriptionOperations` (identity) and `BranchOperations`. Inactive subscription or closed acceptance returns an explanatory 409. This does not revoke panel access or modify existing appointments; `enabled` remains the separate account-access setting. No database migration is needed.

## Customer phone verification — 16 October 2026

1. Obtain CSRF and keep the matching cookie.
2. `POST /api/public/phone-verifications/request` with `{"phone":"+905551112233"}` returns 204. Dev logs a six-digit code (only the last four phone digits are shown).
3. `POST /api/public/phone-verifications/verify` with phone and code returns `verificationToken` and `expiresAt`.
4. Send that token in `X-Phone-Verification` on customer appointment creation. The request phone must match. The proof lasts five minutes and is consumed atomically with successful booking; a rejected booking rolls consumption back. Brand creation does not require this header.

Codes expire after two minutes, allow three wrong guesses, require 60 seconds between sends and allow at most three sends per rolling hour per phone. Resend invalidates previous verification. IP limits add 20 send requests / 15 minutes and 60 verify requests / 15 minutes. Failed guesses commit their counters. Per-phone database locks protect concurrent requests and single-use consumption. Codes and phone lookup keys use HMAC-SHA256 with the configured secret; proof tokens are high-entropy and stored only as hashes. V18 creates verification/rate-history tables. Codes are not returned in HTTP responses and are not automatically retried.

`SmsSender` is the notification module contract. `DevSmsSender` exists only under `dev` without `prod`. Other profiles use `UnavailableSmsSender`, returning 503 without logging codes. A real provider adapter is still required for production. Integration tests use a mock sender; they never send real SMS.

## Brand appointment actions — 17 October 2026

Postman: `Appointments → Appointment Actions`. POST panel endpoints: `/api/panel/appointment/approve-appointment/{appointmentId}`, `/reject-appointment/{appointmentId}` and `/cancel-appointment/{appointmentId}` (same prefix). Use the owning BRAND session and CSRF, with no body. Success returns 200 with an empty body.

Approval/rejection require PENDING. Cancellation accepts PENDING or APPROVED and sets CANCELLED. Missing/foreign records return 404; invalid/repeated transitions return 409. A row lock serializes concurrent decisions. Cancelled/rejected records remain stored and no longer block time; retrying cancellation cannot affect a replacement booking. Cancellation does not require an active subscription or branch acceptance. V19 permits REJECTED and V20 permits CANCELLED; V17 still blocks only PENDING/APPROVED.

Customer cancellation and brand rejection/cancellation SMS enqueueing are implemented. Cancellation cutoff snapshots are implemented in V21. Reimport the collection to see new requests.

## Brand cancellation policy — 17 October 2026

`PATCH /api/panel/brand/cancellation-policy` accepts `{"cancellationNoticeHours":3}` for the authenticated BRAND. CSRF required. Integer hours 0–168; default 0 means until the appointment starts. Missing/null/out-of-range values return 400. The 200 response and GET `/api/panel/brand` include the setting. Postman: Brand → Update Cancellation Policy.

Both brand and customer creation store and return `cancellationDeadline = startsAt - cancellationNoticeHours` as an Instant. Changing the policy affects only new bookings. V21 adds the setting and backfills existing bookings with their start time (initial zero-hour policy). Brand cancellation is not limited by this deadline. Customer token access and deadline enforcement are implemented as described below.

## SMS job storage

V22 adds notification-owned `sms_jobs` and `SmsJobRepository`. Jobs start PENDING with zero attempts; the pending index orders by nextAttemptAt, createdAt and id. Each appointment/type pair is unique to prevent duplicate scheduling. `encryptedBody` must receive encrypted content, never a plaintext token/link. The table stores creation, 24-hour/2-hour reminder, rejection and cancellation jobs; OTP continues through its existing flow.

Appointment creation now enqueues an encrypted SMS in the same transaction. AppointmentSmsWorker selects one due PENDING job with FOR UPDATE SKIP LOCKED, decrypts and sends it, then records SENT. Creation/reminder messages retry once after 10 minutes; rejection/cancellation messages do not retry. The dev sender logs messages; a real provider is not configured. Reminder scheduling and eligibility checks are implemented (see 25 October update). A crash after provider delivery but before database commit can still cause a duplicate; the row lock protects concurrent selection, not exactly-once external delivery.

V23 adds hashed appointment access tokens and expiry (30 days after appointment start). Existing appointments have no issued token. GET /api/public/appointment/details accepts X-Appointment-Token; the creation response does not expose the token. Customer cancellation is implemented; see the customer access section below.

## Customer access and cancellation — 22 October 2026

Postman: `Appointments → Customer Access and Cancellation`. Set `appointmentAccessToken` to the raw token from the SMS link (dev: message in DEV SMS logs). It is not the OTP proof or token hash.

- GET `/api/public/appointment/details` with `X-Appointment-Token` displays details without changing the booking.
- POST `/api/public/appointment/cancel-appointment` with the same header and a valid CSRF cookie/header cancels without login or body.
- Cancellation locks the appointment by token hash. Expired access token returns 410; status other than PENDING/APPROVED or reaching the stored cancellation deadline returns 409; unknown token returns 404. Equality with the deadline is rejected.
- Cancellation stores CANCELLED and one encrypted APPOINTMENT_CANCELLED SMS job atomically; an enqueue failure rolls back cancellation. Repeated/concurrent cancellation cannot enqueue a second SMS. Cancelled bookings no longer block time. Cancellation SMS is not automatically retried.
- Manual flow: Get Details → Cancel Appointment as Customer → Cancel Again (409). Get Details again shows CANCELLED while the access token is valid. New collection requests require reimporting the JSON file.

### Brand decision SMS — 24 October 2026

Brand rejection and cancellation enqueue APPOINTMENT_REJECTED / APPOINTMENT_CANCELLED in the same transaction as the status change. Failed queue persistence rolls back both changes. Repeated/invalid decisions return 409 without another SMS; foreign appointments return 404. Concurrent decisions produce only the winning decision's SMS. Delivery failure has no automatic retry for these message types.

In Postman, use Appointments → Appointment Actions. Test Reject Appointment → Reject Appointment Again (409) on one PENDING booking, then Cancel Appointment → Cancel Appointment Again (409) on a separate PENDING or APPROVED booking. Keep appointmentId unchanged for each repeat. HTTP checks verify responses; database-backed integration tests verify encrypted SMS persistence, transaction rollback and concurrency. A 200 response does not mean the SMS provider has delivered the message.

## Daily branch appointments and reminders — 25 October 2026

`GET /api/panel/appointment/{branchId}/{date}` lists the selected branch's appointments for an Istanbul calendar day (YYYY-MM-DD). Requires the owning BRAND session; no GET body or CSRF header. Anonymous requests return 401, admin 403, missing/foreign branches 404, invalid dates/UUIDs 400. Viewing existing appointments does not require subscriptionActive or acceptingAppointments.

Response is `{"details":[...]}`, including appointmentId, branchId, employeeId, employeeFirstName, employeeLastName, serviceName, durationMinutes, customerFirstName, customerLastName, customerPhone, startsAt, endsAt, appointmentStatus and appointmentSource. Empty days return `{"details":[]}`. No token/hash is exposed. Timestamps are UTC Instants; display them in Europe/Istanbul. The date filter uses startsAt >= local midnight and startsAt < next local midnight. All statuses are included, sorted by startsAt then id.

Appointments and branch employees are fetched in two bulk data queries and joined in memory by UUID (the ownership check may execute an additional query). EmployeeDetails is exported from the employee root package. Missing employee matches produce null name fields without dropping the appointment. Employee names are current; service name/duration remain booking snapshots.

Postman: Appointments → Daily Branch Appointments. Set branchId and scheduleDate. For the foreign-branch check, set otherBrandBranchId to another brand's branch while keeping the original login. Run the anonymous check after logout, then log in again. Reimport the collection. The database integration test covers midnight boundaries, sorting, all statuses, independent branches, inactive employees, snapshots, empty days and access/input errors.

Reminder scheduling is implemented: creation queues 24h/2h jobs only when their due time is now or in the future. Worker rechecks eligibility before each reminder attempt, including retry. Missing, rejected, cancelled or already-started appointments cause SKIPPED, completion time and body clearing without another delivery attempt. Delayed reminders remain eligible before appointment start and contain the actual Istanbul date/time. V24 allows SKIPPED. Real provider integration and a panel SMS delivery-status API remain pending.
