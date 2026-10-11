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

`GET /api/panel/branches/{id}/working-hours` reads the weekly schedule. `PUT` at the same URL replaces the whole schedule with `{ "workingHours": [...] }`; an empty list closes every day. Updates lock the branch to serialize schedule replacements. These endpoints require the owning brand. Existing branches are preserved without inventing default hours; owners must configure their schedules. Branch date-specific exceptions are supported; appointment availability calculation is not implemented yet.

## Account and branch state

| Field | Default at registration | Manager | Meaning |
|---|---|---|---|
| User.enabled | true | Admin | Login permission; disabling revokes existing sessions even if later re-enabled |
| User.subscriptionActive | false | Admin, based on payment | Subscription entitlement, independent of panel login |
| Branch.acceptingAppointments | false | Brand owner | Preference to accept new appointments |

Future booking screens and new appointment creation require `subscriptionActive && acceptingAppointments`. Public booking and appointment creation do not exist yet; no booking-access endpoint is claimed by this release. Deactivating a subscription does not block panel access or existing-booking management. Payment-provider integration is future work.

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

Branch creation UI, availability, appointments, public booking screens, SMS and payment-provider integration. Branch creation/weekly-hours APIs, service creation/listing and employee management/assignments are implemented.

## Employee working hours

All endpoints below are scoped to the employee's owning brand under `/api/panel/employee/{employeeId}`:

| Method | Path | Behavior |
| --- | --- | --- |
| GET | `/working-hours` | Weekly configuration and `usesBranchHours` flag |
| PUT | `/working-hours` | Replace entire custom week with `workingHours` entries (`dayOfWeek`, `startTime`, `endTime`) |
| DELETE | `/working-hours` | Restore branch inheritance, preserving date overrides |
| GET | `/schedule/{date}` | Resolve one date, returning source, active flag, configured and effective hours |
| PUT | `/schedule/{date}` | Replace date override with `workingHours` entries (`startTime`, `endTime`) |
| DELETE | `/schedule/{date}` | Remove date override and fall back to weekly configuration |

No custom weekly record means branch inheritance. A custom week with no intervals means all days closed. An empty date override means that date is closed. Date overrides take precedence over employee weekly settings. Only today/future date overrides may be changed, using Europe/Istanbul to determine today. Intervals must be non-overlapping, within one day and inside branch working hours. Adjacent branch intervals count as continuous opening.

All employee schedule mutations lock the employee row to serialize concurrent updates. Date overrides are unique by employee/date. Effective daily hours always intersect with current branch hours, so later branch changes cannot expose closed time; inactive employees return no effective hours. The weekly endpoint shows configuration, not availability. Existing employees inherit branch hours without a data backfill. Branches without configured hours are closed.

These are schedule APIs, not booking APIs. Appointment conflict checks and available booking slots remain future work. No appointments are cancelled by schedule changes.

## Branch date overrides

`GET /api/panel/branches/{branchId}/schedule/{date}` returns `{date, dateOverride, workingHours}`. `PUT` replaces that date with `{workingHours: [{startTime, endTime}]}`; an empty list explicitly closes it. `DELETE` removes the override and restores the owner-defined weekly schedule. Dates use `YYYY-MM-DD`; edits are limited to today/future dates in Europe/Istanbul. Multiple intervals are allowed without overlap, and a date override can open a normally closed weekday. Writes lock the branch row and persist the header/intervals atomically; employee/date and branch/date overrides are independent.

Employee daily resolution and date validation now use effective branch date hours. Employees inheriting branch hours follow the override directly; employee custom weekly/date hours are intersected with it. Closing a branch yields no employee hours, even with employee date overrides. Restoring branch weekly hours preserves employee customizations. Appointment conflict prevention will be added with booking; these APIs currently manage schedules only.
