# Deploy v2.2.8

1. Stop TrackerAgent on every test device. Do not delete the local SQLite database.
2. Deploy this server package on Render.
3. Keep `FLYWAY_ENABLED=false`, `DB_POOL_MAX_SIZE=5`, `DB_POOL_MIN_IDLE=0`, and `AGENT_DUPLICATE_REPAIR_ENABLED=false`.
4. Set Render Health Check Path to `/health/live`.
5. After the deploy is healthy, start one agent. Start the remaining agents only after five minutes of stable logs.

The current database must already contain Flyway V1-V4, which is true for a database previously used by server v2.2.4.
