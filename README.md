# Sentinel

**Real-time transaction risk decisioning.** A payment system sends Sentinel a card payment; within
milliseconds Sentinel answers **APPROVE**, **STEP_UP** (ask the customer to verify, for example with
an OTP) or **DECLINE**, and records exactly why.

Team Runtime Error (Gurkirpa Singh Sarao, Harkamal Singh, Dhruv Srivastava) · UCS553 Enterprise Web
Applications · TIET

---

## The idea

- A fraud check sits beside every card payment and has to answer in a few milliseconds.
- Sentinel keeps fast counters of recent activity in **Redis** (for example "this card was used 6 times
  this minute") runs a set of **rules** over them, and stores every decision in **PostgreSQL**.
- Each stored decision keeps the facts the rules saw and what each rule did. So any decision can be
  explained later, and a rule change can be tested against past decisions before it goes live.

## What works today (the starter)

- `POST /v1/decisions` decides a payment. Sending the same `transactionId` again returns the same decision.
- `GET /v1/decisions/{transactionId}` shows the stored explanation.
- Six starter rules: large amounts, card velocity, card spend, device velocity, one rule in "shadow
  mode" (recorded but not applied), and a fallback for when Redis is down.
- Keeps deciding in "degraded mode" if Redis is unavailable; refuses (503) if the database is down.
- Unit tests and an end-to-end test, run by GitHub Actions on every push and pull request.
- `simulator/traffic.py` sends a scripted story of payments, including a card-testing attack.

## Plan

The evaluation version comes first: features you can see and explain. The deeper engineering
(concurrency-safe retries, finer time windows, load testing) follows after the end-semester exams.

| Sprint | Goal |
|---|---|
| 0 | Setup: Neon, CI, a console skeleton |
| 1 | See every decision: sign-in with roles, API keys, decision explorer, live feed, new fraud signals, attack scenarios |
| 2 | Change rules safely: rules in the database, versions, publish and rollback, rule builder, Rule Lab (test a rule on past decisions), approvals, audit log |
| 3 | Analyst workflow and polish: dashboard, case queue, fraud labels, screenshots, demo rehearsal |
| After exams | Hardening: concurrency-safe retries, finer windows, circuit breaker, load tests, an ML score |

---

## Setup

### What you need

| Tool | Version | Notes |
|---|---|---|
| Git | any recent | |
| JDK | 21 | Temurin or Oracle; `java -version` must say 21 |
| IDE | IntelliJ IDEA (free edition is enough) | VS Code also works with "Extension Pack for Java" |
| PostgreSQL | 18 (local) or a Neon branch | See step 1 |
| Redis | 7 | Windows: inside WSL (Ubuntu). macOS: Homebrew. Linux: apt |
| Node.js | 20.19 or newer (24 LTS is fine) | Only for the web console |
| Python | 3.11 or newer | Only for the simulator |
| Docker | optional | `ops/docker-compose.yml` can run Postgres and Redis for you instead |

You do not need to install Maven: the project includes the Maven wrapper (`mvnw`), which downloads the
right version the first time you use it.

### 1. Database

Pick one.

**Option A: Neon (nothing to install).** Ask for a branch of the team's Neon project. In the Neon
console, open your branch and copy its connection string, which looks like
`postgresql://USER:PASSWORD@HOST/DBNAME?sslmode=require`. Turn it into these environment variables
(the password never goes in a committed file):

```powershell
$env:SPRING_PROFILES_ACTIVE = "neon"
$env:SPRING_DATASOURCE_URL = "jdbc:postgresql://HOST/DBNAME?sslmode=require"
$env:SPRING_DATASOURCE_USERNAME = "USER"
$env:SPRING_DATASOURCE_PASSWORD = "PASSWORD"
```

Keep those four lines in a script outside the repo and run it (`. path\to\neon-env.ps1`) in each new
terminal before starting the app. Stop the app when you are not using it: Neon's free plan counts
the hours the database is awake.

**Option B: Postgres on your laptop.** Open a Postgres prompt as the admin user (Windows: "SQL Shell
(psql)" from the Start menu; macOS: `psql postgres`; Linux: `sudo -u postgres psql`) and run:

```sql
CREATE USER sentinel WITH PASSWORD 'sentinel';
CREATE DATABASE sentinel OWNER sentinel;
```

Either way, the app creates its own tables when it first starts (Flyway migrations in
`backend/src/main/resources/db/migration`).

### 2. Redis

- **Windows**, in the Ubuntu (WSL) terminal:
  `sudo apt update && sudo apt install -y redis-server && sudo service redis-server start`
- **macOS:** `brew install redis && brew services start redis`
- **Linux:** `sudo apt install redis-server`

Check with `redis-cli ping`, which should answer `PONG`. Without Redis the app still runs, in degraded mode.

**With Docker instead of steps 1 and 2:** `docker compose -f ops/docker-compose.yml up -d`

### 3. Clone and open

```bash
git clone https://github.com/Blakkbird/sentinel.git
```

- Clone into a short path outside OneDrive, for example `D:\dev` or `~/dev`.
- IntelliJ: File → Open → the `sentinel` folder. When it shows "Maven build script found", click **Load**.
  Then File → Project Structure → set the SDK to 21.

### 4. Test and run

From the `backend` folder (in Windows PowerShell, type `.\mvnw` instead of `./mvnw`):

```bash
./mvnw test              # the first run downloads Maven and the libraries (a few minutes)
./mvnw spring-boot:run   # starts the app on http://localhost:8080
```

- The end-to-end test needs Docker. Without Docker it is skipped on your laptop; GitHub's CI still runs it.
- In the app's log, look for `Redis is reachable` and `Started SentinelApplication`.

### 5. The web console

The backend must be running. Then, in a second terminal, from the `console` folder:

```bash
npm ci          # first time, and whenever package-lock.json changes
npm run dev     # opens on http://localhost:5173
```

Vite (the dev server) forwards every `/api` request to the backend on port 8080, so the browser
sees a single site.

### 6. Try it

- Open Swagger UI at http://localhost:8080/swagger-ui.html, choose `POST /v1/decisions`, click
  "Try it out" and send:

  ```json
  {"transactionId": "txn-001", "cardId": "card-42", "deviceId": "dev-7", "merchantId": "mer-1",
   "amountMinor": 150000, "currency": "INR", "occurredAt": "2026-10-15T10:15:30Z"}
  ```

  `amountMinor` is in paise, so 150000 is Rs 1,500.
- Run the simulator from the repo root: `python simulator/traffic.py` (Windows: `py simulator\traffic.py`).
- Health check: http://localhost:8080/actuator/health

### Troubleshooting

- **`java -version` is not 21:** point `JAVA_HOME` at your JDK 21 folder and open a new terminal.
- **"Connection refused" on port 5432:** Postgres is not running (Windows: Services → `postgresql-x64-…`).
- **Neon: the first request after a break is slow:** the database was asleep and is waking up. That is normal.
- **"Redis is NOT reachable" in the log:** start Redis (`sudo service redis-server start` in WSL). The app
  keeps working meanwhile.
- **`./mvnw: Permission denied`** (macOS/Linux): `chmod +x mvnw`.
- **Anything else:** share the *first* error in the output, not the last; later errors are usually
  consequences of the first.

---

## Project structure

```
sentinel/
├── backend/                     the Spring Boot application (Java 21)
│   ├── pom.xml                  build file: libraries and versions
│   ├── mvnw, mvnw.cmd           Maven wrapper
│   └── src/
│       ├── main/java/com/runtimeerror/sentinel/
│       │   ├── engine/          the decision logic: plain Java, no Spring, no database
│       │   ├── features/        builds the facts about a payment (Redis counters)
│       │   ├── rules/           the starter rules and the rule set in use
│       │   ├── decision/        the decision flow and saving to Postgres
│       │   ├── api/             HTTP endpoints, response shapes, error handling
│       │   └── config/          hand-wired objects: clock, engine, Redis client options
│       ├── main/resources/      application.yml (settings), db/migration (SQL)
│       └── test/java/...        unit tests and one end-to-end test
├── console/                     the web console (React, TypeScript, Vite, Tailwind)
│   └── src/                     pages/, components/, lib/ (API client, formatting)
├── simulator/                   Python script that sends test payments
├── ops/                         optional Docker setup for Postgres and Redis
└── .github/                     CI workflow and pull request template
```

**One request, end to end:** check the JSON → if this transaction was already decided, return that
decision → update and read the Redis counters → run the rules → save the decision to Postgres → reply.

**Database:** one table so far, `decision_log`, with one row per decision: the inputs, the outcome
and reasons, the facts the rules saw, and what each rule did. It is defined in `V1__decision_log.sql`.

## API so far

| Method | Path | What it does |
|---|---|---|
| POST | `/v1/decisions` | Decide a payment |
| GET | `/v1/decisions/{transactionId}` | The stored explanation of a decision |
| GET | `/v1/rules/active` | The rules in use, in readable form |
| GET | `/v1/features` | Every fact a rule can use |
| GET | `/actuator/health` | Health of the app, Postgres and Redis |

## Working together

- `main` always works, and nobody pushes to it directly.
- One branch per task (for example `s1/03-auth`), then a pull request, one review from someone else,
  CI green, then squash and merge.
- Small pull requests get better reviews than big ones.
- Nothing merges until its author can explain every line of it.
- Before writing a Flyway migration, claim the next version number in the team chat. Two `V3` files
  stop the app from starting. Never edit a migration that has already run somewhere.

## Tech

Java 21 · Spring Boot 3.5 · PostgreSQL (Neon) · Redis · Flyway · JUnit 5, AssertJ, Testcontainers ·
React 19, TypeScript, Vite, Tailwind CSS, TanStack Query · GitHub Actions · Python (simulator)
