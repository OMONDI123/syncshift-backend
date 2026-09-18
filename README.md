# ShiftSync API

Backend for **Coastal Eats** — a 4-location, 2-timezone restaurant group's staff
scheduling platform. Built for the Priority Soft full-stack assessment on top
of an existing minimal "user management" Spring Boot service, extended into
the full domain described in the brief: shifts, constraints, swaps, presence,
notifications, fairness/overtime analytics, and an admin Setup module.

This repo is the **API only**. The companion `shiftsync-frontend` project is a
fully-built React/Zustand UI prototype that currently runs entirely
client-side (no backend calls yet). This API was designed to be a drop-in
replacement for that prototype's Zustand stores — see **"Frontend
integration status"** below for exactly what that means and what's left to
wire up.

---

## Stack — and why

| Choice | Why |
|---|---|
| **Spring Boot 3.5 / Java 21** | Mature, batteries-included framework for exactly this shape of app — REST + auth + real-time + JPA — without stitching together separate libraries for each concern. Java 21 gets records and pattern matching, which keep the DTO and constraint-violation code (a lot of "what kind of thing is this" branching) compact and readable. |
| **PostgreSQL** | The constraint engine, audit trail, and analytics all lean on relational integrity (foreign keys between shifts/users/locations, `@Version` optimistic locking, row-level locking for the assignment race) and on aggregate queries over shift history — a real RDBMS is the right tool for that, and Postgres is the production-shaped, free, widely-supported default. |
| **Spring Data JPA / Hibernate** | Keeps the entity model and the constraint engine's domain vocabulary (`ViolationCode`, `Severity`, etc.) in one place as plain Java objects, with `ddl-auto: update` handling schema evolution during development without a separate migration step for this project's scope. |
| **Spring Security + JWT (jjwt)** | Stateless auth fits a SPA + WebSocket client cleanly — no server-side session store to keep in sync across REST and STOMP. The same bearer token authenticates both. |
| **STOMP over SockJS (Spring WebSocket)** | Matches the frontend's `@stomp/stompjs` client directly, and gives topic-based pub/sub (`/topic/locations/{id}/schedule`, `/topic/users/{id}/notifications`, etc.) instead of hand-rolling raw WebSocket message routing. |
| **Package-by-feature (`user`, `schedule`, `swap`, `analytics`, ...)** | The domain has enough independent concerns (scheduling, swaps, presence, analytics, audit) that grouping by feature rather than by layer (controller/service/repo folders) keeps each one's files together and easier to reason about in isolation. |

---

## Quick start

Requires a reachable PostgreSQL instance.

```bash
# 1. Create the database and a role for it (adjust to taste)
createdb shiftsync
psql -d shiftsync -c "CREATE USER shiftsync WITH PASSWORD 'shiftsync';"
psql -d shiftsync -c "GRANT ALL PRIVILEGES ON DATABASE shiftsync TO shiftsync;"
```

```bash
# 2. Run the backend
export DATABASE_URL=jdbc:postgresql://localhost:5432/shiftsync
export DATABASE_USERNAME=shiftsync
export DATABASE_PASSWORD=shiftsync
./mvnw spring-boot:run
```

Runs on `http://localhost:8080/api`. Hibernate creates the schema automatically
on first run (`ddl-auto: update`) and seeds demo data unless you set
`SEED_ENABLED=false`.

- Swagger UI: `http://localhost:8080/api/docs`
- WebSocket (STOMP over SockJS): `http://localhost:8080/api/ws`

### Production profile

```bash
SPRING_PROFILES_ACTIVE=prod \
DATABASE_URL=jdbc:postgresql://<host>:5432/shiftsync \
DATABASE_USERNAME=... DATABASE_PASSWORD=... \
JWT_SECRET=<a long random string> \
CORS_ALLOWED_ORIGINS=https://your-frontend.example.com \
./mvnw spring-boot:run
```

Or build a jar: `./mvnw clean package` → `target/shiftsync-backend-1.0.0.jar`.

### Logging in

Login is real, signed-JWT auth (`POST /api/auth/login`), but every seeded
account shares **one password per role** so the assessment login screen is
trivial to demo without ten different credentials to remember (see
`user/DemoPasswords.java`):

| Role    | Example email               | Password       |
|---------|------------------------------|-----------------|
| Admin   | `dana@coastaleats.com`       | `Admin@123`     |
| Manager | `marcus@coastaleats.com`     | `Manager@123`   |
| Manager | `priya@coastaleats.com`      | `Manager@123`   |
| Staff   | `sarah@coastaleats.com`      | `Staff@123`     |
| Staff   | any other seeded staff email | `Staff@123`     |

Full seeded roster is in `config/DataSeeder.java`. New users created via
`POST /users` also get their role's demo password, for the same reason —
obviously not how you'd provision real accounts in production (see
Limitations).

`POST /auth/login` → `{ token, expiresInMinutes, user }`. Send
`Authorization: Bearer <token>` on every subsequent REST call and on the
STOMP `CONNECT` frame (same token, native header `Authorization`).

---

## Architecture at a glance

- **Spring Boot 3.5 / Java 21**, package-by-feature under `co.ke.shiftsync`:
  `user`, `location`, `skill`, `availability`, `schedule` (shifts + the
  constraint engine), `swap`, `notification`, `presence`, `audit`,
  `settings` (admin Setup module), `analytics`, `auth`, `security`, `ws`
  (WebSocket), `common` (shared enums/exceptions), `config` (seed data).
- **JWT auth**, stateless, `spring-security` + `jjwt`. Per-resource
  permission checks (e.g. "does this manager run this location?") live in
  `security/PermissionService`, called from every mutating service method —
  not just the controller layer, matching the frontend prototype's own
  `lib/auth.ts` design note that hiding a button isn't access control.
- **Real-time**: STOMP over SockJS at `/ws`. Every schedule change, swap
  update, notification, and on-duty change is pushed live — see
  `ws/WebSocketConfig` for the topic list.
- **Constraint engine**: `schedule/ConstraintService` is a line-for-line port
  of the frontend prototype's `lib/constraints.ts`, so the two systems agree
  on every rule, every violation code, and every alternative-staff
  suggestion. `schedule/ShiftService` is the transactional/permission/
  concurrency layer around it.
- **Concurrency**: shifts carry a JPA `@Version` (optimistic lock); staff
  assignment additionally takes a **pessimistic row lock on the staff
  member being assigned** before re-checking constraints, so the
  "Simultaneous Assignment" scenario (two managers, two different shifts,
  same person, at the same instant) resolves correctly even though the two
  shifts are different database rows. See the javadoc on
  `UserRepository.lockById` and `ShiftService.assign` for the full
  reasoning.
- **Persistence**: PostgreSQL via JPA entities.

---

## Requirement → module map

| Brief requirement | Where |
|---|---|
| 1. Users & roles | `user/*`, `security/PermissionService` |
| 2. Shift scheduling & constraints | `schedule/*` (esp. `ConstraintService`, `ShiftService`) |
| 3. Swaps & coverage | `swap/*` |
| 4. Overtime & compliance | Warnings inline in `ConstraintService`; cost dashboard in `analytics/AnalyticsService.overtimeProjection` |
| 5. Fairness analytics | `analytics/AnalyticsService.distribution` |
| 6. Real-time | `ws/*`, called from `schedule`, `swap`, `presence`, `notification` |
| 7. Notifications | `notification/*` (email is simulated — logged, never sent) |
| 8. Calendar/timezone handling | `schedule/TimeUtil`, `availability/AvailabilityWindow` javadoc |
| 9. Audit trail | `audit/*` |
| Admin **Setup module** (requested in addition to the brief) | `settings/*` (skills catalog, locations, constraint thresholds — all admin-only, all editable at runtime instead of hardcoded) |

---

## Frontend integration status

The `shiftsync-frontend` repo you provided is a complete, working UI
*prototype* — every store (`scheduleStore`, `swapStore`, `availabilityStore`,
`notificationStore`, `presenceStore`) currently holds its own state in memory
via Zustand, with no network calls at all. That prototype's `lib/constraints.ts`,
`lib/auth.ts`, and `types/index.ts` were the spec this API was built against,
so the domain models, violation codes, and workflows line up closely by
design. **Wiring the frontend to this API (replacing each store's in-memory
state with fetch/STOMP calls) is separate follow-on work**, not included
here, per your instructions to focus this pass on the backend. A few
concrete notes for whoever does that pass:

- The frontend was mid-refactor on its own admin Setup page
  (`pages/admin/SetupPage.tsx` not yet built) when handed off — this API's
  `/setup/*` endpoints are exactly what that page will need once built.
- Every feature present in the brief but not yet in the UI mockup — the
  Setup module itself, audit export, clock-in/out presence, the overtime
  cost dashboard, and the assignment "what-if" preview — **is implemented
  here on the backend already**, ready for the UI to catch up to, per your
  instruction to build ahead of the mockup where the brief calls for more
  than the mockup currently shows.
- Wire format: DTOs were named and shaped to mirror `types/index.ts` closely
  (e.g. `ViolationCode`, `NotificationType` match field-for-field) to
  minimize translation work on the frontend side.

---

## Documented decisions on the brief's "Intentional Ambiguities"

1. **De-certifying a staff member from a location** — removing a location
   from `certifiedLocationIds` is always allowed and never touches history:
   past shifts, audit entries, and swap records keep referencing that
   person/location exactly as they were. It only affects *future*
   assignability (they'll fail the `LOCATION_NOT_CERTIFIED` check going
   forward). A location itself can't be deleted while any shift, certified
   staff member, or manager still references it (`LocationService.delete`).
2. **"Desired hours" vs. availability** — treated as independent signals.
   Desired hours feeds fairness analytics (are they under/over relative to
   what they asked for); availability is a hard constraint. Someone can
   declare 40 desired hours while only being available 20 — the fairness
   report will show them chronically under, which is itself useful
   information for a manager, rather than the system silently reconciling
   the two.
3. **Consecutive-day counting** — a 1-hour shift and an 11-hour shift count
   identically: any shift on a given *location-local* calendar day counts
   that day toward the consecutive-day streak. Simpler and harder to game
   than hour-weighting, and matches how labor law consecutive-day rules
   typically work in practice.
4. **Editing a shift after swap approval but before it occurs** — always
   allowed (managers need this), but the edit re-validates every currently
   assigned person against the new details; anyone the edit now makes
   unqualified/conflicted is automatically removed with a notification
   explaining why (`ShiftService.update`), and — the brief's named edge
   case — any *other* pending swap/drop tied to that shift is
   auto-cancelled (`ShiftEditedEvent` → `SwapService.onShiftEdited`).
5. **A location spanning a timezone boundary** — not supported; a location
   picks exactly one governing IANA timezone (`Location.timezone`). Modeling
   a single restaurant as split across two zones adds real complexity for a
   scenario that's rare in practice; documented as a deliberate scope cut
   rather than a half-implementation.

## Other assumptions worth flagging

- **`hourlyRate` and `NotificationChannel`** were added to `AppUser` beyond
  the brief's explicit fields — the overtime-cost dashboard and the
  in-app-vs-email notification preference both need *something* concrete to
  work against.
- **Overtime cost model**: hours above the weekly full-time threshold
  (default 40, admin-configurable) are costed at 1.5× `hourlyRate`. Common
  in the US but not universal; clearly a modeling choice, not a computed
  fact.
- **Fairness score**: `100 − coefficient_of_variation(premium shift counts)
  × 100`, floored at 0. 100 = perfectly even distribution of Friday/Saturday
  evening shifts across a location's certified staff. Not specified
  numerically in the brief; documented in `AnalyticsService.distribution`'s
  javadoc rather than presented as an unambiguous "correct" formula.
- **"Premium" shift** = starts at/after 5pm local time on a Friday or
  Saturday. A number, not in the brief; documented in `ShiftService`.
- **48-hour publish/edit cutoff override**: the brief only names a manager
  override for the 7th-consecutive-day rule explicitly. Editing/unpublishing
  a *published* shift within the cutoff window is extended the same
  override pattern (manager or admin, with a mandatory recorded reason)
  since forbidding it outright with no escape hatch didn't seem workable for
  a real business; audited either way.
- **Editing availability on someone else's behalf**: staff edit their own;
  admins can edit anyone's; managers can edit availability for staff
  certified at a location they run. Not specified in the brief.

---

## Concurrency & real-time notes for evaluators

- **The Simultaneous Assignment scenario**: `POST /shifts/{id}/assign`
  acquires a pessimistic write lock on the target staff member's row before
  re-running the constraint check. Two managers racing to assign the same
  bartender to two different shifts will serialize: whichever request's
  transaction commits first wins cleanly; the second sees the
  now-committed data and correctly receives a `DOUBLE_BOOKED` or
  `REST_PERIOD` violation (HTTP 422) instead of a silent double-assignment.
  A losing optimistic-lock race on the *same* shift (two managers editing
  the same shift's own fields) surfaces as HTTP 409 and also pushes a
  `/topic/shifts/{id}/conflict` WebSocket message so an open UI can react
  immediately, per the brief's "one should see a conflict notification
  immediately."
- **No-refresh updates**: every shift/swap/notification/presence mutation
  broadcasts over STOMP (`/topic/locations/{id}/schedule`,
  `/topic/locations/{id}/swaps`, `/topic/users/{id}/notifications`,
  `/topic/locations/{id}/presence`) in addition to being persisted, so a
  connected client never needs to poll.
- **Drop expiry** runs on a 15-minute `@Scheduled` job
  (`SwapService.expireStaleDrops`), not "24 hours before the shift" as a
  literal cron trigger — it re-checks all open drops on each tick and flips
  anything past its precomputed `expiresAtUtc`.

---

## Evaluation scenario walkthroughs (using the seed data)

- **Sunday Night Chaos**: `u-john` already has an *open* drop request
  (`SwapKind.DROP`, status `OPEN`) on the Sunday 7pm Seattle Harbor server
  shift. `GET /shifts/{id}/check-assignment?userId=...` or a fresh
  `POST /swaps/request-drop` flow will surface qualified pickups via the
  suggestion engine.
- **Overtime Trap**: `u-ava` (Miami Shore) is already at 40 scheduled hours
  for the week; the unfilled Saturday line-cook shift is seeded specifically
  to demonstrate `POST /shifts/{id}/check-assignment` flagging the overtime
  warning before a manager confirms it.
- **Timezone Tangle**: `u-jordan` is certified at both Seattle Harbor
  (Pacific) and Miami Shore (Eastern), home timezone Pacific, with a
  "9am–5pm every day" recurring availability window. Check a Miami shift
  against them — the window is evaluated in *their* Pacific time, not
  Miami's Eastern time, exactly as documented in `ConstraintService`.
- **Simultaneous Assignment**: the unfilled Saturday 6pm Miami bartender
  shift qualifies both `u-jordan` and `u-liam` — fire two concurrent
  `POST /shifts/{id}/assign` calls at it (as `marcus`/`priya`, or any two
  managers of that location) to see the lock in action.
- **Fairness Complaint**: `GET /analytics/locations/{id}/distribution` for
  any date range returns exact premium-shift counts per staff member,
  plus the single-number fairness score.
- **Regret Swap**: the seeded record (Sarah ↔ Jordan, Friday Seattle
  double-server shift) sits at `PENDING_MANAGER`. Calling
  `POST /swaps/{id}/cancel` as Sarah before a manager approves it flips it
  to `CANCELLED` — the underlying shift assignment is untouched, because
  `managerApprove` is the *only* place that ever mutates it.

---

## Known limitations

- **This sandbox couldn't reach Maven Central**, so `mvn compile` was never
  actually run here — the code was written and manually reviewed (package/
  import/signature consistency checked file-by-file) but **not
  compiler-verified**. Please run `./mvnw clean verify` first thing; if
  anything doesn't compile it should be a small, mechanical fix (missing
  import or similar) rather than a design problem, but flag it back if it's
  more than that.
- No automated test coverage beyond `ConstraintServiceTest` and
  `TimeUtilTest` (pure unit tests, no Spring context / DB needed). A real
  submission should add `@SpringBootTest` + Testcontainers integration tests
  for the swap workflow, concurrency scenario, and REST layer — the
  `testcontainers`/`spring-boot-starter-test` dependencies are already in
  `pom.xml` for exactly that.
- User provisioning uses one demo password per role (see above) rather than
  individual credentials or an invite/reset flow — fine for this assessment,
  not how you'd ship real account provisioning.
- Email notifications are simulated (logged via `NotificationService`,
  never sent) per the brief's explicit "email simulation" ask — no SMTP
  integration.
- No pagination on most list endpoints (`GET /shifts`, `GET /users`, etc.)
  except audit search — fine at this data scale, would need addressing for
  a larger deployment.
- `AnalyticsService.distribution`/`overtimeProjection` load a location's
  shifts into memory and aggregate in Java rather than via SQL aggregation
  — fine at this data scale, would want to move to query-level aggregation
  before scaling up.

---

## Deploying to a public URL

Any platform that runs a Spring Boot jar against a Postgres database works
(Render, Railway, Fly.io, a small VM, etc.). Steps are the same everywhere:

1. Provision a Postgres instance.
2. Set `SPRING_PROFILES_ACTIVE=prod`, `DATABASE_URL`, `DATABASE_USERNAME`,
   `DATABASE_PASSWORD`, `JWT_SECRET` (long random string — **do not** ship
   the dev default), and `CORS_ALLOWED_ORIGINS` (your deployed frontend's
   origin) as environment variables.
3. Build: `./mvnw clean package -DskipTests` → deploy
   `target/shiftsync-backend-1.0.0.jar`.
4. First boot seeds the same demo data described above against the fresh
   Postgres database (set `SEED_ENABLED=false` to skip on subsequent
   deploys/restarts once you have real data you don't want touched).