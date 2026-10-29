# Postman Guide

Import `Appointment Platform.postman_collection.json` into Postman. This README is a usage guide, not an import file.

## Collection folders

| Folder | Purpose |
| --- | --- |
| Authentication / Session | Get or refresh CSRF, inspect the current account, and log out |
| Authentication / Registration | Register a brand and verify its email address |
| Authentication / Login | Sign in as a brand or admin |
| Brand | Read the current brand, capture its slug, and update its cancellation policy |
| Branches | Create branches, configure weekly/date-specific hours, and control appointment acceptance |
| Services | Create and list services |
| Employees | Create and list employees; manage working hours |
| Employees / Service Assignments | Assign services, list assignments, and remove assignments |
| Appointments / Availability | Find available appointment start times |
| Appointments / Brand Creation | Create appointments from the brand panel |
| Appointments / Customer Creation | Verify a customer's phone and create an appointment |
| Appointments / Appointment Actions | Approve, reject, or cancel appointments as the brand |
| Appointments / Customer Access and Cancellation | View or cancel an appointment using its access token |
| Appointments / Daily Branch Appointments | List a branch's appointments for a selected day |
| Public Booking | Look up a brand and its accepting branches by slug |
| Admin | List accounts and update account status |
| System | Check health and the verification page's HTML/JavaScript |

`Validation Checks` folders intentionally contain invalid requests. Expected `400` or `409` responses count as successful tests.

## Getting started

1. Set `baseUrl` and the required account credentials in the collection variables.
2. Run **Authentication → Session → Get CSRF**.
3. Choose **Brand Login** or **Admin Login** under **Authentication → Login**. Do not run both consecutively unless you intend to switch accounts.
4. Run **Authentication → Session → Refresh CSRF After Login**.
5. Open the relevant operation folder. Postman sends the session cookie automatically.

Do not run the entire collection as one scenario: login requests switch roles, registration requires an email token, and some requests modify existing data.

After a collection update, reimport the JSON file. Save any customized variable values first and avoid confusing the old and new copies. Postman environment variables can override collection variables; check the effective values when troubleshooting.

## Employee–service assignments

After signing in as a brand:

1. Use **Branches → Create Branch** to provide the address, open days, and working hours. The example uses Monday–Saturday, 08:00–20:00; adjust it as needed. Registration does not create a branch. For an existing branch, **Get Own Branches** saves the first branch ID as `branchId`.
2. Run **Services → Get Own Services** to save the first service ID as `serviceId`. If the list is empty, use **Add Service** first.
3. Run **Employees → Add Employee** to save the created ID as `employeeId`. To use an existing employee, set the variable manually.
4. Run **Employees → Service Assignments → Assign Service**.
5. Check the result with **Get Assigned Services**.
6. Optionally run **Validation Checks → Reject Duplicate Assignment**; expect `409`.
7. Run **Unassign Service**, then **Get Assigned Services** to confirm the assignment was removed.

Change the collection's ID variables to work with other records.

## Branch working hours

Read the weekly schedule with **Get Working Hours** and replace the entire week with **Replace Working Hours**. Days omitted from the list are closed; an empty list closes every day.

Under **Branches → Specific Date**, use the Get, Set, Close, and Reset requests. Set `branchId` and `scheduleDate`. The date is part of the URL; the body contains only `workingHours`.

An empty date-specific list closes that branch for the selected date. Employee overrides cannot extend beyond branch hours. Reset removes only the date override and restores the weekly schedule. Set can open a normally closed day. Employees inheriting branch hours use those hours; employees with their own configuration remain subject to that configuration.

## Employee working hours

Use **Employees → Working Hours**:

- Employees initially inherit branch hours.
- **Set Weekly Hours** replaces the full weekly configuration. Days in `workingHours` use custom intervals; days in `closedDays` are closed. Days in neither list inherit branch hours. If both lists are empty, every day inherits branch hours.
- **Reset to Branch Hours** removes the custom weekly configuration but preserves date overrides.
- For date requests, set `scheduleDate` to today or a future date in `YYYY-MM-DD` format.
- **Close Employee for Date** closes only that date. **Reset Date to Weekly Hours** removes that date override and resolves the schedule again.
- **Get Effective Hours for Date** returns selected intervals in `configuredHours` and intervals restricted by branch hours in `workingHours`. Inactive employees have no effective working hours.
- These are working intervals, not appointment slots. The appointment availability endpoint subtracts existing `PENDING` and `APPROVED` bookings.

### Weekly and date-specific folders

- **Weekly Schedule:** view, replace, or reset weekly settings.
- **Specific Date:** view or replace a date's hours, close the date, or reset it.
- DELETE requests remove schedule overrides; they do not delete the employee.

The weekly response uses `configuredHours` for custom intervals, `closedDays` for explicitly closed weekdays, `inheritedDays` for weekdays inherited from the branch, and `workingHours` for the combined result. When editing, build the PUT request from `configuredHours` and `closedDays`; do not send the combined result back as the custom schedule.

Daily precedence is: employee date override → employee weekday configuration → branch hours for that date. An empty `workingHours` date override explicitly closes the date; it does not fall back to branch hours. DELETE removes that override and reapplies the precedence rules.

V13 preserves all seven closed weekdays for employees whose earlier empty weekly plans closed the entire week. Missing days in previously populated plans now inherit branch hours. Use **Reset to Branch Hours** to remove those explicit closures.

### Troubleshooting a 404

The weekly endpoint is:

```http
GET {{baseUrl}}/api/panel/employee/{{employeeId}}/working-hours
```

Run **Get Employee** with the same `employeeId`. Check the selected ID and whether the signed-in brand owns the employee's branch. If Get Employee returns `200` but Get Weekly Hours returns `404`, verify the URL and restart the backend with the updated controller. Check for environment variables overriding `employeeId`.

Missing schedule records do not cause `404`: branch hours are inherited, or an empty list is returned if the branch has no hours.

## Available appointment start times

Use **Appointments → Availability → Get Available Slots**:

1. Sign in as a brand.
2. Set `employeeId`, `serviceId`, and `scheduleDate`. The service must be assigned to the employee.
3. Send the GET request without a body.
4. Read start times from `availableAppointmentSlotDetails.localTimes`.

Start times advance according to the selected service duration, and the entire service must fit within a working interval. The endpoint lists starts at least one hour ahead using `Europe/Istanbul`. An empty list is expected for past dates or closed/inactive employees.

The endpoint subtracts `PENDING` and `APPROVED` bookings but does not reserve a time. Creation checks availability and conflicts again.

## Appointment creation

- **Appointments → Brand Creation → Create Appointment as Brand** requires a brand session. The brand can book past or near-term times if the appointment fits the applicable working hours.
- **Appointments → Customer Creation → Create Appointment as Customer** supports anonymous customers. Log out and obtain a fresh CSRF token; the CSRF cookie and header must belong to the same session. Customer bookings must start at least one hour ahead.
- Set `branchId`, `employeeId`, `serviceId`, `scheduleDate`, and `appointmentStartTime`. The employee must belong to the selected branch, and the service must belong to the brand and be assigned to that employee.
- `customerEmail` is optional. Do not send `source`; the backend assigns it. Successful creation returns `201` with status `PENDING`, stores `appointmentId` in Postman, and returns start/end times as UTC Instants.
- Customer creation requires OTP verification. Both creation paths require an active subscription and an accepting branch. Overlapping bookings return `409`; V17 also protects against concurrent double booking at the database level.

Integration tests cover customer and brand creation, persisted source/timestamps, optional email, incorrect branches, working-hour limits, customer lead time, brand past-time booking, and anonymous panel-access rejection.

### Permission to create new bookings

Use an admin account to set the brand account's `subscriptionActive` to `true`. Then sign in as the brand and run **Branches → Open Appointment Acceptance**. Refresh CSRF after switching accounts.

An inactive subscription or closed appointment acceptance returns `409` with an explanation in `detail`. If both are enabled and all other checks pass, creation returns `201`. These settings do not block panel login, remove existing bookings, or prevent viewing them.

For anonymous customer testing, log out, refresh CSRF, and follow the OTP steps below.

### Customer OTP

Under **Appointments → Customer Creation**:

1. Run **01 Request SMS Code** with `customerPhone`. In the dev profile, copy the code from the backend's `DEV SMS` log into `smsCode`.
2. Run **02 Verify SMS Code**. The response automatically saves `phoneVerificationToken`.
3. Run **Create Appointment as Customer** with the same phone and the `X-Phone-Verification` header. Successful creation consumes the proof. A new appointment requires new verification; a failed creation can be retried while the proof remains valid.

The SMS code is valid for two minutes and allows at most three incorrect attempts. Wait at least 60 seconds before requesting another code; a phone number may receive at most three codes per hour. A `429` response includes `Retry-After`. The verification proof is valid for five minutes. Neither a code nor a proof can be used with a different phone number.

Brand creation does not require OTP. A real SMS provider is not configured: the dev profile logs messages, while production sending returns `503`.

## Brand appointment actions

Use **Appointments → Appointment Actions** with the owning brand's session, a refreshed CSRF token, and no request body:

```http
POST /api/panel/appointment/approve-appointment/{appointmentId}
POST /api/panel/appointment/reject-appointment/{appointmentId}
POST /api/panel/appointment/cancel-appointment/{appointmentId}
```

Success returns `200` with an empty body. Creation saves `appointmentId` automatically.

- Approval and rejection require `PENDING`.
- Cancellation accepts `PENDING` or `APPROVED` and sets `CANCELLED`.
- Missing or foreign appointments return `404`; invalid or repeated transitions return `409`.
- A row lock serializes concurrent decisions. Rejected/cancelled records remain stored but no longer block time. Retrying cancellation cannot affect a replacement booking.
- Brand cancellation does not require an active subscription or an accepting branch and is not limited by the customer's cancellation deadline.

Use separate pending appointments to test approval and rejection. Test **Reject Appointment → Reject Appointment Again (409)** on one booking and **Cancel Appointment → Cancel Appointment Again (409)** on another. Keep the same ID for each repeated action.

V19 adds `REJECTED`, V20 adds `CANCELLED`, and V17 blocks time only for `PENDING`/`APPROVED`.

### Decision SMS

Brand rejection and cancellation enqueue `APPOINTMENT_REJECTED` or `APPOINTMENT_CANCELLED` in the same transaction as the status change. Queue persistence failure rolls back both changes. Invalid/repeated decisions do not enqueue another SMS; concurrent decisions produce only the winning decision's message.

These SMS types do not automatically retry after delivery failure. A `200` response confirms the transaction, not delivery by the SMS provider. Postman checks HTTP responses; database integration tests check encrypted persistence, rollback, and concurrency.

## Brand cancellation policy

Use **Brand → Update Cancellation Policy**:

```http
PATCH /api/panel/brand/cancellation-policy
```

```json
{"cancellationNoticeHours": 3}
```

The authenticated brand session and CSRF are required. The value must be an integer from 0 to 168; default 0 means cancellation is allowed until the appointment starts. Missing, null, or out-of-range values return `400`. The update response and `GET /api/panel/brand` include the setting.

Both creation paths store and return `cancellationDeadline = startsAt - cancellationNoticeHours` as an Instant. Later policy changes affect only new bookings. V21 backfills existing bookings with their start time, reflecting the initial zero-hour policy. Customer cancellation requires the current time to be strictly before this deadline; equality is rejected.

## Customer access and cancellation

Use **Appointments → Customer Access and Cancellation**. Set `appointmentAccessToken` to the raw token from the SMS link. In dev, obtain it from the `DEV SMS` log. It is not the OTP proof or the database token hash. Creation does not return the access token.

- GET `/api/public/appointment/details` with `X-Appointment-Token` reads details without modifying the appointment.
- POST `/api/public/appointment/cancel-appointment` with the same token header and a valid CSRF cookie/header cancels without login or a body.
- Unknown tokens return `404`; expired access tokens return `410`. Invalid status or a reached cancellation deadline returns `409`.
- Cancellation locks the appointment by token hash and atomically stores `CANCELLED` plus one encrypted cancellation SMS job. An enqueue failure rolls back cancellation. Repeated/concurrent requests cannot enqueue a second cancellation message.
- Cancelled appointments no longer block time. Cancellation SMS does not automatically retry.

Manual flow: **Get Appointment Details → Cancel Appointment as Customer → Cancel Again (409)**. Reading details again shows `CANCELLED` while the access token remains valid. The access token expires 30 days after the appointment starts; this does not extend the cancellation deadline.

## Daily branch appointments

Use **Appointments → Daily Branch Appointments**:

```http
GET /api/panel/appointment/{branchId}/{date}
```

Set `branchId` and `scheduleDate` (`YYYY-MM-DD`). The date represents an Istanbul calendar day. The owning brand session is required; no GET body or CSRF header is needed. Anonymous requests return `401`, admin requests `403`, missing/foreign branches `404`, and malformed dates/UUIDs `400`.

The response is `{"details":[...]}`. Empty days return `{"details":[]}`. Fields include:

- `appointmentId`, `branchId`, `employeeId`
- `employeeFirstName`, `employeeLastName`
- `serviceName`, `durationMinutes`
- `customerFirstName`, `customerLastName`, `customerPhone`
- `startsAt`, `endsAt`, `appointmentStatus`, `appointmentSource`

No access token or hash is exposed. Timestamps are UTC Instants; display them in `Europe/Istanbul`. The query includes starts at local midnight and excludes starts at the next local midnight. All statuses are included, ordered by `startsAt` then `id`.

Appointments and branch employees are fetched in two bulk data queries and matched by UUID in memory; ownership validation may execute another query. Missing employee matches produce null names without dropping the appointment. Employee names are current, while service name/duration are booking snapshots. Viewing existing appointments does not require an active subscription or appointment acceptance.

For the foreign-branch check, set `otherBrandBranchId` to another brand's branch while keeping the original login. Run the anonymous check after logout, then log in again. Integration tests cover day boundaries, ordering, statuses, branch isolation, inactive employees, snapshots, empty results, and access/input errors.

## Reminder delivery

Creation schedules 24-hour and 2-hour reminder jobs only if their due time is now or in the future. Past reminder thresholds do not create jobs.

Before every reminder attempt, including retries, the worker checks whether the appointment exists, is `PENDING`/`APPROVED`, and has not started. Ineligible jobs become `SKIPPED`: the completion time is recorded, the encrypted body is cleared, and no additional delivery attempt is counted. V24 permits this status.

A delayed reminder can still be sent before the appointment starts. Messages contain the actual Istanbul date/time rather than an inaccurate remaining-hours claim. Creation/reminder SMS failures retry once after ten minutes; rejection/cancellation and OTP messages do not automatically retry.

A real SMS provider and a panel API for viewing SMS delivery status remain pending. Worker locking prevents concurrent selection of the same job; it cannot guarantee exactly-once delivery if the process stops after provider delivery but before database commit.

## Public booking URL

Brands have an immutable, unique `slug` with a maximum length of 120 characters. New brands use a normalized ASCII display-name prefix plus the full brand UUID without hyphens. Turkish letters are transliterated; names with no supported characters use `brand` as the prefix. The UUID suffix avoids duplicate-name collisions. V25 assigns `brand-<uuid>` to existing brands without changing their IDs or display names. There is no slug-edit endpoint; the database enforces non-null, unique, lowercase URL-safe values.

`GET /api/panel/brand` and cancellation-policy responses include `slug`. The planned frontend route is `/randevu/{slug}`; this backend does not serve that page. It provides:

```http
GET /api/public/brands/{slug}
```

No authentication, CSRF, or appointment token is required. The response contains `id`, `displayName`, `slug`, and `branches`. Each branch contains only `id`, `name`, `address`, and `googleMapsUrl`.

Only the matching brand's branches with `acceptingAppointments=true` are returned, ordered by `createdAt` then `id`. If no branch accepts bookings, the array is empty. Unknown slugs return `404`. Discovery does not grant booking permission or bypass subscription, schedule, OTP, or overlap checks during creation.

Brand looks up branches through `BranchOperations` and `PublicBranchDetails`, without accessing the branch entity/repository. Public branch-service discovery is available under Public Booking. Public employee discovery is available under Public Booking → Employees; public availability discovery is also implemented below; existing panel endpoints have not been made public.

In Postman:

1. Run **Brand → Get Own Brand** to save `brandSlug`.
2. Run **Public Booking → Get Brand by Slug**.
3. Log out and repeat to verify anonymous access.
4. Run **Unknown Brand Slug (404)** to check the missing-brand response.

The slug identifies a business. The SMS access token identifies one appointment; they are separate values with different purposes.

## Branch service assignments

Services remain in the brand catalog. Use **Services → Branch Assignments** to select which services a branch offers.

- `PUT /api/panel/branches/{branchId}/services/{serviceId}` assigns a service.
- `DELETE /api/panel/branches/{branchId}/services/{serviceId}` removes only that assignment.
- Both require a BRAND session and CSRF token, take no body, and return `204`. Repeated requests are safe.
- Both the branch and service must belong to the signed-in brand; otherwise the response is `404`.
- **Public Booking → Get Branch Services** lists these assignments without login.

Migration V26 creates an empty assignment table; it does not automatically assign existing catalog services. Employee assignments and existing appointments are unchanged.

Implementation: branch assignments use JPA save/delete and a pessimistic lock on the service row. The public list uses JPQL. Existing endpoint URLs and request bodies are unchanged by this implementation change.

Branch-service membership is now checked during employee assignment, available-time listing, and both brand/customer appointment creation. Missing membership returns 400 with "Bu hizmet seçilen şubede sunulmuyor." Removing an assignment preserves existing employee assignments and appointments, but blocks new bookings until the service is reassigned to the branch.

### Branch membership checks

Order: create a brand service → assign it to the branch → assign it to an employee → request availability/create a booking. Use **Services → Branch Assignment Checks (Manual)** after removing the branch assignment. Run these checks manually with valid prerequisite data, then reassign the service. PUT/DELETE requests include the CSRF header. Reimport the updated collection into Postman.

## Public employee discovery

`GET /api/public/employee/branches/{branchId}/services/{serviceId}` requires no login, CSRF token or body. Select a branch and one of its assigned services first. The response is a list of `id`, `branchId`, `firstName`, `lastName`, `active`. Only active employees assigned to that service in that branch are returned, ordered by firstName, lastName and id. A valid branch-service assignment with no matching employees returns `200 []`. Missing branch-service membership (including unknown IDs) returns `400`; malformed UUIDs also return `400`.

The endpoint uses EmployeeService and an EmployeeRepository JPQL query. ServiceOperations checks branch-service membership; service does not depend on employee. No migration is required. This lists eligible active employees, not free appointment times; subscription, branch acceptance and date/time availability are not checked here.

Postman: **Public Booking → Employees**. Reimport the collection, set branchId and serviceId, then choose an employee ID from the response for later requests.

## Public appointment availability

`GET /api/public/appointment/available-slot/{branchId}/{serviceId}/{employeeId}/{date}` is anonymous and accepts no body or CSRF token. Use YYYY-MM-DD for date. The backend resolves brandId from the branch and verifies that the employee belongs to that branch. Active subscription and branch appointment acceptance are required.

Public and panel endpoints share `getAvailableStartTimes`. Service ownership, branch-service and employee-service assignments are checked. The response is `{"availableAppointmentSlotDetails":{"serviceId":"...","employeeId":"...","localTimes":["09:00:00"]}}`. Times are Europe/Istanbul local times, at least one hour ahead; the complete service must fit in working hours and PENDING/APPROVED appointments block their intervals. Closed days, inactive employees or no fitting times return 200 with an empty localTimes array. This GET does not reserve a slot; creation checks again.

Errors: malformed UUID/date or mismatched employee branch → 400; missing service assignments → 400; unknown branch/employee/service → 404; inactive subscription or appointment acceptance disabled → 409. Existing panel endpoints still require BRAND login. No migration was added.

Postman: **Public Booking → Available Slots**. Set branchId, serviceId, employeeId and scheduleDate. The closed-booking case is a manual negative test; restore the disabled setting afterwards.
