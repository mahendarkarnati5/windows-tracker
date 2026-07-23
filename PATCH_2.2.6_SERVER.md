# Tracker Server 2.2.6 — performance and consistency patch

This is a server-only release. Keep Windows Agent 2.2.5 and the existing React dashboard.

## Main changes

- One database transaction per agent sync batch instead of one `REQUIRES_NEW` transaction and one legacy-device lock per activity record.
- Removed per-record `SELECT ... FOR UPDATE` on the legacy device row.
- Removed per-record `saveAndFlush`; the batch flushes at commit.
- Runtime projection duplicate repair is disabled by default. It cannot scan/lock production tables unless `AGENT_DUPLICATE_REPAIR_ENABLED=true` is explicitly set.
- Presence/offline reconciliation uses bounded batches and a separate short transaction for each device.
- Exact process/idle/session duplicates are repaired by Flyway before traffic starts and protected by unique indexes.
- Near-identical process snapshots reuse the same projection when PID, process name and overlapping lifetime indicate one OS process instance.
- Hikari pool is bounded and fails quickly instead of accumulating long connection wait queues.

## Render deployment

Deploy this folder as the backend source. No duplicate-repair timing variables are required.

Recommended environment values:

```text
AGENT_DUPLICATE_REPAIR_ENABLED=false
AGENT_PRESENCE_CHECK_MS=15000
AGENT_ORPHAN_REPAIR_MS=300000
AGENT_PRESENCE_BATCH_SIZE=50
DB_POOL_MAX_SIZE=10
DB_POOL_MIN_IDLE=2
DB_CONNECTION_TIMEOUT_MS=5000
```

Do not set `AGENT_DUPLICATE_REPAIR_ENABLED=true` during normal production traffic.

## Deployment order

1. Stop agent processes on test devices.
2. Back up the production database.
3. Deploy server 2.2.6 and wait for Flyway V5, V6 and V7 success.
4. Confirm there are no Hikari timeout errors.
5. Start one agent, observe for five minutes, then start the remaining agents.
