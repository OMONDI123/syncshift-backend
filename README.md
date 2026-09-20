# ShiftSync API

## The problem I set out to solve

I built this as my submission to a Priority Soft full-stack take-home assessment: the
backend for Coastal Eats, a 4-location, 2-timezone restaurant group's staff scheduling
platform. I started from an existing minimal "user management" Spring Boot service they
gave me and extended it into the full domain the brief called for: shifts, an
eligibility/constraint engine, swaps, presence, notifications, fairness/overtime
analytics, and an admin Setup module.

My approach was to treat this as a real scheduling system, not a CRUD API with a
calendar table:

- **I enforce permissions server-side, not just in the UI.** Admin (org-wide), Manager
  (their own locations only), and Staff (their own shifts/availability) each have
  boundaries I check in every mutating service method via `PermissionService` — hiding
  a button isn't access control.
- **I built an actual constraint engine.** Assigning someone to a shift checks skill
  match, location certification, availability (converted through the *staff member's
  own* timezone, not the shift's), minimum rest between shifts, daily/weekly hour
  limits, and 6th/7th-consecutive-day rules, with a human-readable reason for every
  block or warning and ranked alternative-staff suggestions.
- **I handle the concurrency cases explicitly, not by luck.** Shifts carry a JPA
  `@Version` for optimistic locking; assigning staff additionally takes a pessimistic
  row lock on the staff member being assigned before re-checking constraints, so two
  managers racing to assign the same person to two different shifts serialize correctly
  instead of silently double-booking.
- **I made everything real-time**, not polled: every schedule change, swap update,
  notification, and presence change pushes over STOMP/SockJS.
- **I computed fairness and overtime analytics from real data** — each person's actual
  hourly rate and actual assignment history — rather than a flat assumed rate.
- **I logged a full audit trail** of every schedule change and denied action, searchable
  and exportable to CSV.
- **I made the admin Setup module runtime-configurable**: locations, the skills
  catalog, and the constraint engine's own thresholds (rest hours, daily/weekly limits,
  swap caps) can all change without a redeploy.

I scoped this deliberately to "get the right person on the right shift, fairly and
within the rules" — I didn't build a payroll system, a POS integration, or a
general-purpose HR tool.

## Stack — and why I chose it

| Choice | Why I chose it |
|---|---|
| **Spring Boot 3.5 / Java 21** | It's a mature, batteries-included framework for exactly this shape of app — REST + auth + real-time + JPA — without me stitching together separate libraries for each concern. Java 21 gave me records and pattern matching, which kept my DTO and constraint-violation code (a lot of "what kind of thing is this" branching) compact and readable. |
| **PostgreSQL** | My constraint engine, audit trail, and analytics all lean on relational integrity (foreign keys between shifts/users/locations, `@Version` optimistic locking, row-level locking for the assignment race) and on aggregate queries over shift history — a real RDBMS is the right tool for that, and Postgres is the production-shaped, free, widely-supported default. |
| **Spring Data JPA / Hibernate** | Keeps my entity model and the constraint engine's domain vocabulary (`ViolationCode`, `Severity`, etc.) in one place as plain Java objects, with `ddl-auto: update` handling schema evolution during development without a separate migration step at this project's scope. |
| **Spring Security + JWT (jjwt)** | Stateless auth fits a SPA + WebSocket client cleanly — I don't need a server-side session store to keep in sync across REST and STOMP. The same bearer token authenticates both. |
| **STOMP over SockJS (Spring WebSocket)** | Matches the frontend's `@stomp/stompjs` client directly, and gave me topic-based pub/sub (`/topic/locations/{id}/schedule`, `/topic/users/{id}/notifications`, etc.) instead of hand-rolling raw WebSocket message routing. |
| **Package-by-feature (`user`, `schedule`, `swap`, `analytics`, ...)** | The domain has enough independent concerns — scheduling, swaps, presence, analytics, audit — that grouping by feature rather than by layer (controller/service/repo folders) keeps each one's files together and easier for me to reason about in isolation. |

## Getting started

Requires a reachable PostgreSQL instance.

**1. Database**

```bash
createdb shiftsync
psql -d shiftsync -c "CREATE USER shiftsync WITH PASSWORD 'shiftsync';"
psql -d shiftsync -c "GRANT ALL PRIVILEGES ON DATABASE shiftsync TO shiftsync;"
```

**2. Run it**

```bash
export DATABASE_URL=jdbc:postgresql://localhost:5432/shiftsync
export DATABASE_USERNAME=shiftsync
export DATABASE_PASSWORD=shiftsync
./mvnw spring-boot:run
```

This runs on `http://localhost:8080/api`. Hibernate creates the schema automatically on
first run (`ddl-auto: update`) and seeds demo data unless you set `SEED_ENABLED=false`.

- Swagger UI: `http://localhost:8080/api/docs`
- WebSocket (STOMP over SockJS): `http://localhost:8080/api/ws`

**Production profile**

```bash
SPRING_PROFILES_ACTIVE=prod \
DATABASE_URL=jdbc:postgresql://<host>:5432/shiftsync \
DATABASE_USERNAME=... DATABASE_PASSWORD=... \
JWT_SECRET=<a long random string> \
CORS_ALLOWED_ORIGINS=https://your-frontend.example.com \
./mvnw spring-boot:run
```

Or build a jar: `./mvnw clean package` → `target/shiftsync-backend-1.0.0.jar`.
