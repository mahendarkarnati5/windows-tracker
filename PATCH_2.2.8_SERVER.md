# Tracker Server v2.2.8 Stable Startup

This release removes web-startup database work that caused Render's 15-minute port timeout.

- Flyway is disabled by default for the existing V1-V4 production schema.
- Heavy V5-V7 cleanup migrations are not included in the runtime artifact.
- Hibernate schema validation and JDBC metadata discovery are disabled at startup.
- Hikari starts lazily with zero minimum idle connections.
- Default maximum pool size is five, leaving capacity during Render overlap deployments.
- Duplicate repair remains disabled in live traffic.
- `/health/live` is a database-independent Render health endpoint.
- v2.2.6 batch-sync and idempotent projection logic is preserved.

Use `FLYWAY_ENABLED=true` only for a new empty database. Do not enable it on the current production database without a reviewed migration window.
- Admin bootstrap database access is disabled by default, so an existing admin configuration cannot block port binding.
