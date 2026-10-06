# Appointment Platform — Backend

Java 21, Spring Boot 4.1.1, Spring Modulith 2.1.1, PostgreSQL, JPA, Flyway and server-side Spring Security sessions.

## Domain and modules

- `identity`: accounts, email verification, login, password reset, admin account status management.
- `brand`: brand identity and registration orchestration.
- `branch`: physical branches, address/maps data and appointment-acceptance preference.
- `notification`: transactional encrypted mail queue and worker.
- `audit`: security and management events scoped by `brandId`.
- `shared`: crypto, rate limits and API errors.

Dependencies: Identity → Brand → Branch; Identity → Notification; Identity and Branch → Audit. Modules access only their own repositories. Public module contracts are at package roots. `ApplicationModules.verify()` checks the declared boundaries.

One BRAND account owns one Brand through a unique `users.brand_id`. ADMIN accounts have no brand. A Brand can have multiple Branch records; the current registration creates only the first “Merkez” branch. There is no separate Store entity or add-branch API yet.

`POST /api/auth/registrations` keeps the existing flat request. `displayName` names the Brand; `address` and `googleMapsUrl` belong to its first Branch. Email verification creates account, brand and branch in one transaction.

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

Branch creation UI/API, services, employees, availability, appointments, public booking screens, SMS and payment-provider integration.
