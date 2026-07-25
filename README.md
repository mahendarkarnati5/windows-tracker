# Tracker Server 2.3.0

Spring Boot 3 / Java 21 / MySQL-compatible server for strict local-first agent synchronization.

## Build

```bash
./mvnw clean verify
```

## Run

```bash
java -jar target/tracker-backend-2.3.0.jar
```

Required environment:

```text
TIDB_URL
TIDB_USER
TIDB_PASSWORD
JWT_SECRET
```

Dashboard origin configuration:

```text
CORS_ALLOWED_ORIGINS=http://localhost:5173,https://your-dashboard.vercel.app
```

## Render

Use the included `Dockerfile`, set health check path to `/health/live`, and clear build cache when replacing an older 2.2.x deployment. The final Docker build executes `mvn clean verify`; a failed test cannot produce the runtime image.

Flyway is enabled. V8 adds strict natural-key and dashboard lookup indexes. Missing historical V5-V7 migrations from older experimental packages are ignored, while already-applied copies remain accepted.

There is no periodic duplicate-repair scan. Duplicate prevention is performed during the normal per-device sync transaction and by database unique natural keys.
