# Tracker Server v2.2.8 - Stable Startup and Fast Sync

# Tracker Server

Tracker Server accepts idempotent activity snapshots from Tracker Agent v2 while continuing to populate the existing reporting tables used by the admin application.

## Required environment

| Variable | Required | Description |
| --- | --- | --- |
| `TIDB_URL` | yes | MySQL/TiDB JDBC URL, for example `jdbc:mysql://host:4000/database?sslMode=VERIFY_IDENTITY` |
| `TIDB_USER` | yes | Database username |
| `TIDB_PASSWORD` | yes | Database password |
| `JWT_SECRET` | yes | Random HMAC secret of at least 32 bytes |
| `JWT_EXPIRE` | no | User JWT lifetime in milliseconds; default `86400000` |
| `PORT` | no | HTTP port; default `8080` |
| `AGENT_OFFLINE_AFTER_SECONDS` | no | Presence timeout; default `150` |
| `AGENT_PRESENCE_CHECK_MS` | no | Presence check interval; default `60000` |
| `ADMIN_BOOTSTRAP_USERNAME` | first deployment only | Initial admin username when no admin exists |
| `ADMIN_BOOTSTRAP_PASSWORD` | first deployment only | Initial admin password; at least 12 characters |

Do not commit these values. The supplied Render manifest declares them as secret environment variables.

## Agent API

| Method and path | Authentication | Purpose |
| --- | --- | --- |
| `POST /api/auth/register` | public | Create a BCrypt-protected user |
| `POST /api/auth/login` | public | Return a signed user JWT |
| `PUT /api/auth/admin/users/{id}/password` | admin JWT | Set or migrate a user's BCrypt password |
| `POST /api/v1/agent/devices/enroll` | user JWT | Bind a stable device UUID to the authenticated user and rotate its sync credential |
| `POST /api/v1/agent/devices/{uuid}/heartbeat` | device credential or user JWT | Update presence without activity writes |
| `PUT /api/v1/agent/devices/{uuid}/activities` | device credential or user JWT | Apply up to 100 current activity snapshots |

A device credential is 256 random bits. The response returns it once during enrollment, the database stores only its SHA-256 hash, and the credential is accepted only for the matching device path under `/api/v1/agent`. It cannot enroll another device or call reporting APIs.

Each activity is keyed by `recordUuid`. The server locks an existing row, applies only a higher `revision`, treats the same revision as an idempotent replay, and acknowledges a lower revision as stale. A primary-key race on two first inserts is retried in a new transaction. Each batch row has its own ACK, so one rejected row cannot block the rest.

## Database and reporting compatibility

Flyway exclusively owns production schema changes; Hibernate runs with `ddl-auto=validate`.

+ `V1__baseline_and_agent_sync.sql` safely creates the legacy reporting tables when absent, then adds `agent_devices` and `agent_activities`.
+ `V2__agent_device_credentials.sql` adds the hashed device credential.
+ `agent_activities.record_uuid` is the primary key. Indexes cover device/start, user/start, and device/state queries.
+ `agent_devices.device_uuid` is unique and ownership cannot move between users.

Every accepted canonical snapshot is projected in the same transaction into the existing `process_activity`, `active_window_activity`, `idle_activity`, or `device_session` row. This keeps the current dashboards and exports working. A migrated client's `legacyRecordId` is accepted only when that old row belongs to the enrolled device.

All timestamps are normalized to UTC. Canonical duration is stored in milliseconds; legacy projections use whole seconds.

## Security boundaries

+ Registration and login are the only public application endpoints.
+ Admin registration and every `/api/admin/**` or legacy device/activity mutation require `ROLE_ADMIN`.
+ User reporting endpoints derive identity from the authenticated principal; the legacy stats-by-ID endpoint rejects access to another user unless the caller is an admin.
+ CORS is restricted to the configured local/admin frontends.
+ Invalid JWTs and device credentials do not populate the Spring Security context.

## Build and test

JDK 21 and Maven 3.9+ are required.

```powershell
mvn test
mvn clean package
java -jar target\tracker-backend-0.0.1-SNAPSHOT.jar
```

Tests use H2 in MySQL mode and real Flyway migrations. They cover replay/out-of-order revisions, projection, row-level batch rejection, credential hashing/scope, HTTP filter authentication, and Spring context startup.

## Production rollout

1. Back up the production database and record the current Flyway/schema state.
2. Set all required environment variables, especially a 32-byte-or-longer `JWT_SECRET`.
3. On a fresh database, set the two bootstrap-admin variables for the first deployment, verify creation, then remove both secrets.
4. Deploy the server first. Verify Flyway V1 and V2 completed and application logs show no validation errors.
5. Use the admin password-reset endpoint for any legacy account whose password column is empty, then exercise login, enrollment, one open revision, and its closing revision on a staging device.
6. Deploy the client installer. Its first run imports only unsynced legacy local rows and retains their old server IDs.
7. Monitor `agent_activities`, rejected client rows, and outbox depth before broad rollout.

Never edit an already-applied Flyway migration. Add a new versioned migration for later schema changes.

## Built-in admin dashboard

The server now serves a dependency-free admin dashboard from `/` on the same port as the API. The login screen uses `POST /api/auth/admin`; every dashboard data request remains protected by `ROLE_ADMIN`.

Dashboard flow:

1. Sign in as an admin and view total, online, offline, and shutdown device cards.
2. Click a card to open the corresponding device table.
3. Open Users, select a user, then select one of that user's devices.
4. The dedicated device view shows today's process count, running processes, the latest session, current idle state, and current active window.
5. Full information provides Processes, Active windows, Idle periods, and Sessions tabs with server-side pagination, partial text matching after two characters, date/status/time filters, sortable columns, and filtered-duration totals.

The default activity view is Today in descending order. Filtered duration is deliberately not calculated or displayed until the admin applies a filter, which avoids an unnecessary aggregate query on initial page load.


## v2.2.7 database startup settings

Recommended Render values:

```text
DB_CONNECTION_TIMEOUT_MS=30000
DB_VALIDATION_TIMEOUT_MS=5000
DB_CONNECT_RETRIES=10
DB_CONNECT_RETRY_INTERVAL=5s
AGENT_DUPLICATE_REPAIR_ENABLED=false
```

Do not set MySQL `connectTimeout` or `socketTimeout` to 5000 in `TIDB_URL`.
